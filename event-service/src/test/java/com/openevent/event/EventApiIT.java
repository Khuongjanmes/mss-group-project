package com.openevent.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import com.openevent.common.messaging.Messaging;
import com.openevent.common.messaging.events.EventCancelled;
import com.openevent.event.client.IdentityClient;
import com.openevent.event.media.ImageStorage;

import feign.FeignException;
import feign.Request;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Luồng nghiệp vụ của Event qua HTTP thật, PostgreSQL và RabbitMQ thật. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "openevent.events.completion-interval=PT0.2S"})
class EventApiIT {

    @ServiceConnection
    static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:16-alpine");

    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    static {
        DB.start();
        RABBIT.start();
    }

    record Caller(UUID id, String roles) {
    }

    record Response(int status, JsonNode body) {
        String code() {
            return body.path("code").asString();
        }

        String text(String field) {
            return body.path(field).asString();
        }
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final Caller admin = new Caller(UUID.randomUUID(), "USER,ADMIN");
    private final Caller organizer = new Caller(UUID.randomUUID(), "USER");
    private final Caller otherOrganizer = new Caller(UUID.randomUUID(), "USER");
    private final Caller attendee = new Caller(UUID.randomUUID(), "USER");
    private final Caller outsider = new Caller(UUID.randomUUID(), "USER");
    private final Caller member = new Caller(UUID.randomUUID(), "USER");

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    AmqpAdmin amqp;

    @Autowired
    TopicExchange exchange;

    @MockitoBean
    IdentityClient identity;

    @MockitoBean
    ImageStorage images;

    /** User mà Identity giả báo là đã bị khóa. */
    private final Set<UUID> locked = new HashSet<>();

    /** Identity giả: email `<uuid>@test.local` thuộc user có id đó; email khác không tồn tại. */
    @BeforeEach
    void fakeIdentity() {
        when(identity.getByEmail(anyString())).thenAnswer(call -> {
            String email = call.getArgument(0);
            if (!email.endsWith("@test.local")) {
                throw new FeignException.NotFound("không có", Request.create(Request.HttpMethod.GET, "/", Map.of(),
                        null, StandardCharsets.UTF_8, null), null, Map.of());
            }
            return contact(UUID.fromString(email.substring(0, email.indexOf('@'))));
        });
        when(identity.getBatch(any())).thenAnswer(call -> ((IdentityClient.UserBatch) call.getArgument(0)).ids()
                .stream().map(this::contact).toList());
    }

    private IdentityClient.UserContact contact(UUID id) {
        return new IdentityClient.UserContact(id, id + "@test.local", "Người dùng " + id.toString().substring(0, 4),
                locked.contains(id) ? "LOCKED" : "ACTIVE", id.equals(admin.id()));
    }

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H'};

    /** Gửi một tệp multipart field {@code file}, như trình duyệt gửi. */
    private Response upload(String eventId, String contentType, byte[] bytes, Caller caller) {
        String boundary = "openevent-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"cover.png\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(bytes);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/events/" + eventId + "/images"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("X-User-Id", caller.id().toString()).header("X-User-Roles", caller.roles())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), json.readTree(response.body()));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String email(Caller caller) {
        return caller.id() + "@test.local";
    }

    private Response send(String method, String path, Object body, Caller caller) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "application/json")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (caller != null) {
                request.header("X-User-Id", caller.id().toString()).header("X-User-Roles", caller.roles());
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode node = response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
            return new Response(response.statusCode(), node);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String createCategory(String name) {
        Response response = send("POST", "/api/categories", Map.of("name", name), admin);
        assertThat(response.status()).isEqualTo(201);
        return response.text("id");
    }

    private String createVenue(String city) {
        Response response = send("POST", "/api/venues",
                Map.of("name", "Hội trường A", "address", "1 Lê Duẩn", "city", city, "capacity", 500), admin);
        assertThat(response.status()).isEqualTo(201);
        return response.text("id");
    }

    private Map<String, Object> eventBody(String title, String categoryId, String venueId, Instant start, Instant end) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("description", "Mô tả");
        body.put("categoryId", categoryId);
        body.put("venueId", venueId);
        body.put("startTime", start.toString());
        body.put("endTime", end.toString());
        return body;
    }

    private Map<String, Object> futureEvent(String title, String city) {
        Instant start = Instant.now().plus(7, ChronoUnit.DAYS);
        return eventBody(title, createCategory("Hội thảo " + UUID.randomUUID()), createVenue(city), start,
                start.plus(3, ChronoUnit.HOURS));
    }

    private String createDraft(Caller owner, Map<String, Object> body) {
        Response response = send("POST", "/api/events", body, owner);
        assertThat(response.status()).isEqualTo(201);
        assertThat(response.text("status")).isEqualTo("DRAFT");
        return response.text("id");
    }

    private String publish(Caller owner, Map<String, Object> body) {
        String id = createDraft(owner, body);
        assertThat(send("PUT", "/api/events/" + id + "/submit", null, owner).text("status")).isEqualTo("PENDING_APPROVAL");
        assertThat(send("PUT", "/api/events/" + id + "/approve", null, admin).text("status")).isEqualTo("PUBLISHED");
        return id;
    }

    @Test
    void categorySlugIsGeneratedAndDuplicateNameIsRejected() {
        String name = "Âm nhạc Đường phố " + UUID.randomUUID().toString().substring(0, 8);
        Response created = send("POST", "/api/categories", Map.of("name", name), admin);

        assertThat(created.text("slug")).startsWith("am-nhac-duong-pho-");
        assertThat(send("POST", "/api/categories", Map.of("name", name.toUpperCase()), admin).code())
                .isEqualTo("CATEGORY_EXISTS");
        assertThat(send("POST", "/api/categories", Map.of("name", "X"), organizer).status()).isEqualTo(403);
    }

    @Test
    void fullLifecycleFromDraftToPublicListing() {
        String city = "Đà Nẵng " + UUID.randomUUID();
        String id = createDraft(organizer, futureEvent("Workshop Spring", city));

        assertThat(send("GET", "/api/events/" + id, null, null).status()).isEqualTo(404);
        assertThat(send("GET", "/api/events/" + id, null, organizer).status()).isEqualTo(200);

        send("PUT", "/api/events/" + id + "/submit", null, organizer);
        Response rejected = send("PUT", "/api/events/" + id + "/reject", Map.of("reason", "Thiếu ảnh bìa"), admin);
        assertThat(rejected.text("status")).isEqualTo("REJECTED");
        assertThat(rejected.text("rejectReason")).isEqualTo("Thiếu ảnh bìa");

        Response resubmitted = send("PUT", "/api/events/" + id + "/submit", null, organizer);
        assertThat(resubmitted.text("status")).isEqualTo("PENDING_APPROVAL");
        assertThat(resubmitted.body().path("rejectReason").isNull()).isTrue();
        assertThat(send("PUT", "/api/events/" + id + "/approve", null, admin).text("status")).isEqualTo("PUBLISHED");

        Response listing = send("GET",
                "/api/events?keyword=spring&city=" + URLEncoder.encode(city, StandardCharsets.UTF_8), null, null);
        assertThat(listing.status()).isEqualTo(200);
        assertThat(listing.body().path("totalElements").asLong()).isEqualTo(1);
        assertThat(listing.body().path("content").get(0).path("id").asString()).isEqualTo(id);
        assertThat(send("GET", "/api/events/" + id, null, null).status()).isEqualTo(200);
    }

    @Test
    void onlyOwnerEditsAndOnlyWhileDraftOrRejected() {
        Map<String, Object> body = futureEvent("Hội thảo AI", "Huế");
        String id = createDraft(organizer, body);

        assertThat(send("PUT", "/api/events/" + id, body, otherOrganizer).code()).isEqualTo("NOT_EVENT_OWNER");
        assertThat(send("PUT", "/api/events/" + id, body, organizer).status()).isEqualTo(200);

        send("PUT", "/api/events/" + id + "/submit", null, organizer);
        assertThat(send("PUT", "/api/events/" + id, body, organizer).code()).isEqualTo("EVENT_STATUS_INVALID");
        assertThat(send("DELETE", "/api/events/" + id, null, organizer).code()).isEqualTo("EVENT_STATUS_INVALID");
    }

    @Test
    void timeRulesAreEnforced() {
        Map<String, Object> body = futureEvent("Sai giờ", "Hà Nội");
        Instant start = Instant.parse((String) body.get("startTime"));

        body.put("endTime", start.minus(1, ChronoUnit.HOURS).toString());
        assertThat(send("POST", "/api/events", body, organizer).code()).isEqualTo("INVALID_EVENT_TIME");

        body.put("startTime", Instant.now().minus(1, ChronoUnit.DAYS).toString());
        body.put("endTime", Instant.now().plus(1, ChronoUnit.DAYS).toString());
        assertThat(send("POST", "/api/events", body, organizer).code()).isEqualTo("INVALID_EVENT_TIME");
    }

    @Test
    void anyAccountCreatesButOnlyAdminApproves() {
        String id = createDraft(attendee, futureEvent("Ai cũng tạo được", "Huế"));

        assertThat(send("PUT", "/api/events/" + id + "/approve", null, admin).code()).isEqualTo("EVENT_STATUS_INVALID");
        assertThat(send("PUT", "/api/events/" + id + "/approve", null, attendee).status()).isEqualTo(403);
    }

    @Test
    void adminReviewsButDoesNotOrganizeEvents() {
        Map<String, Object> body = futureEvent("Admin không tổ chức", "Huế");

        assertThat(send("POST", "/api/events", body, admin).status()).isEqualTo(403);
        assertThat(send("GET", "/api/events/my", null, admin).status()).isEqualTo(403);
        assertThat(send("POST", "/api/organizations", Map.of("name", "CLB Admin"), admin).status()).isEqualTo(403);

        String id = createDraft(organizer, body);
        assertThat(send("PUT", "/api/events/" + id, body, admin).status()).isEqualTo(403);
        assertThat(send("GET", "/api/events/" + id, null, admin).status()).isEqualTo(200);
    }

    @Test
    void cancelPublishesEventCancelledAndOtherOrganizerCannotCancel() {
        Queue probe = QueueBuilder.nonDurable("event-cancel-probe").autoDelete().build();
        amqp.declareQueue(probe);
        amqp.declareBinding(BindingBuilder.bind(probe).to(exchange).with(Messaging.EVENT_CANCELLED));
        String id = publish(organizer, futureEvent("Sẽ hủy", "Đà Nẵng"));

        assertThat(send("PUT", "/api/events/" + id + "/cancel", null, otherOrganizer).code()).isEqualTo("NOT_EVENT_OWNER");
        assertThat(send("PUT", "/api/events/" + id + "/cancel", null, organizer).text("status")).isEqualTo("CANCELLED");

        Object received = rabbit.receiveAndConvert("event-cancel-probe", Duration.ofSeconds(10).toMillis());
        assertThat(received).isInstanceOfSatisfying(EventCancelled.class,
                event -> assertThat(event.eventId()).isEqualTo(UUID.fromString(id)));
        assertThat(send("PUT", "/api/events/" + id + "/cancel", null, organizer).code()).isEqualTo("EVENT_STATUS_INVALID");
    }

    @Test
    void internalSummaryMatchesFeignContract() {
        String id = publish(organizer, futureEvent("Nội bộ", "Huế"));

        Response summary = send("GET", "/internal/events/" + id, null, null);

        assertThat(summary.status()).isEqualTo(200);
        assertThat(summary.text("organizerId")).isEqualTo(organizer.id().toString());
        assertThat(summary.text("status")).isEqualTo("PUBLISHED");
        assertThat(summary.body().has("startTime")).isTrue();
        assertThat(summary.body().has("endTime")).isTrue();
        assertThat(send("GET", "/internal/events/" + UUID.randomUUID(), null, null).code()).isEqualTo("EVENT_NOT_FOUND");
    }

    @Test
    void categoryAndVenueInUseCannotBeDeleted() {
        Map<String, Object> body = futureEvent("Đang dùng", "Huế");
        createDraft(organizer, body);

        assertThat(send("DELETE", "/api/categories/" + body.get("categoryId"), null, admin).code())
                .isEqualTo("CATEGORY_IN_USE");
        assertThat(send("DELETE", "/api/venues/" + body.get("venueId"), null, admin).code()).isEqualTo("VENUE_IN_USE");
    }

    @Test
    void endedPublishedEventsBecomeCompleted() throws Exception {
        String id = publish(organizer, futureEvent("Đã qua", "Huế"));
        jdbc.update("update events set start_time = now() - interval '2 hours', end_time = now() - interval '1 hour' "
                + "where id = ?::uuid", id);

        Instant deadline = Instant.now().plusSeconds(10);
        while (!"COMPLETED".equals(send("GET", "/api/events/" + id, null, null).text("status"))
                && Instant.now().isBefore(deadline)) {
            Thread.sleep(200);
        }
        assertThat(send("GET", "/api/events/" + id, null, null).text("status")).isEqualTo("COMPLETED");
    }

    private String createOrganization(Caller owner) {
        Response response = send("POST", "/api/organizations", Map.of("name", "FPT Event Club"), owner);
        assertThat(response.status()).isEqualTo(201);
        assertThat(response.text("myRole")).isEqualTo("OWNER");
        return response.text("id");
    }

    private Response access(String eventId, Caller caller) {
        return send("GET", "/internal/events/" + eventId + "/access?userId=" + caller.id(), null, null);
    }

    private Response invite(String org, Caller who, String role, Caller by) {
        return send("POST", "/api/organizations/" + org + "/members", Map.of("email", email(who), "role", role), by);
    }

    @Test
    void ownerAndManagerManageEveryEventOfTheirOrganization() {
        Caller owner = organizer;
        Caller manager = otherOrganizer;
        String org = createOrganization(owner);
        String members = "/api/organizations/" + org + "/members";
        Response invited = invite(org, manager, "MANAGER", owner);
        assertThat(invited.status()).isEqualTo(201);
        assertThat(invited.text("fullName")).startsWith("Người dùng");
        assertThat(invite(org, outsider, "MANAGER", manager).code()).isEqualTo("ORGANIZATION_PERMISSION_DENIED");
        assertThat(send("POST", members, Map.of("email", "khong-ton-tai@example.com", "role", "MEMBER"), owner).code())
                .isEqualTo("USER_NOT_FOUND");

        Map<String, Object> body = futureEvent("Sự kiện của CLB", "Huế");
        body.put("organizationId", org);
        String id = createDraft(manager, body);

        assertThat(send("PUT", "/api/events/" + id, body, owner).status()).isEqualTo(200);
        assertThat(send("PUT", "/api/events/" + id, body, outsider).code()).isEqualTo("NOT_EVENT_OWNER");
        assertThat(send("POST", "/api/events", body, outsider).code()).isEqualTo("ORGANIZATION_NOT_FOUND");
        JsonNode listed = send("GET", "/api/organizations/" + org + "/events", null, owner).body().path("content").get(0);
        assertThat(listed.path("id").asString()).isEqualTo(id);
        assertThat(listed.path("organizationName").asString()).isEqualTo("FPT Event Club");
        assertThat(send("GET", "/api/organizations/" + org, null, outsider).code()).isEqualTo("ORGANIZATION_NOT_FOUND");
        assertThat(access(id, owner).body().path("canManage").asBoolean()).isTrue();
        assertThat(access(id, outsider).body().path("canCheckIn").asBoolean()).isFalse();

        // BR-14: HOST phải được thay trước khi rời tổ chức.
        assertThat(send("DELETE", members + "/" + manager.id(), null, owner).code()).isEqualTo("MEMBER_HOSTS_EVENTS");
        assertThat(send("PUT", "/api/events/" + id + "/host", Map.of("userId", outsider.id()), owner).code())
                .isEqualTo("HOST_NOT_MEMBER");
        Response reassigned = send("PUT", "/api/events/" + id + "/host", Map.of("userId", owner.id()), owner);
        assertThat(reassigned.text("organizerId")).isEqualTo(owner.id().toString());
        assertThat(send("DELETE", members + "/" + manager.id(), null, manager).status()).isEqualTo(204);
        assertThat(send("PUT", "/api/events/" + id, body, manager).code()).isEqualTo("NOT_EVENT_OWNER");
    }

    @Test
    void memberCreatesEventsOnlyWhenGrantedAndManagesOnlyOwnEvents() {
        Caller owner = organizer;
        Caller manager = otherOrganizer;
        String org = createOrganization(owner);
        invite(org, manager, "MANAGER", owner);
        assertThat(invite(org, member, "MEMBER", manager).body().path("canCreateEvents").asBoolean()).isFalse();

        Map<String, Object> body = futureEvent("Sự kiện của MEMBER", "Huế");
        body.put("organizationId", org);
        assertThat(send("POST", "/api/events", body, member).code()).isEqualTo("CANNOT_CREATE_EVENT");

        String memberPath = "/api/organizations/" + org + "/members/" + member.id();
        Map<String, Object> grant = Map.of("role", "MEMBER", "canCreateEvents", true);
        assertThat(send("PUT", memberPath, grant, manager).code()).isEqualTo("NOT_ORGANIZATION_OWNER");
        assertThat(send("PUT", memberPath, grant, owner).body().path("canCreateEvents").asBoolean()).isTrue();
        assertThat(send("GET", "/api/organizations/" + org, null, member).body().path("canCreateEvents").asBoolean())
                .isTrue();

        String own = createDraft(member, body);
        String managers = createDraft(manager, body);
        assertThat(send("PUT", "/api/events/" + own, body, member).status()).isEqualTo(200);
        assertThat(send("PUT", "/api/events/" + managers, body, member).code()).isEqualTo("NOT_EVENT_OWNER");
        assertThat(send("PUT", "/api/events/" + own, body, manager).status()).isEqualTo(200);

        // MANAGER xóa được MEMBER nhưng không xóa được OWNER; MEMBER đang làm HOST thì phải đổi HOST trước.
        assertThat(send("DELETE", "/api/organizations/" + org + "/members/" + owner.id(), null, manager).code())
                .isEqualTo("ORGANIZATION_PERMISSION_DENIED");
        assertThat(send("DELETE", memberPath, null, manager).code()).isEqualTo("MEMBER_HOSTS_EVENTS");
        send("DELETE", "/api/events/" + own, null, member);
        assertThat(send("DELETE", memberPath, null, manager).status()).isEqualTo(204);
    }

    @Test
    void ownershipTransferKeepsExactlyOneOwner() {
        String org = createOrganization(organizer);
        invite(org, otherOrganizer, "MEMBER", organizer);

        Response transferred = send("PUT", "/api/organizations/" + org + "/owner",
                Map.of("userId", otherOrganizer.id()), organizer);
        assertThat(transferred.status()).isEqualTo(200);
        assertThat(transferred.text("myRole")).isEqualTo("MANAGER");
        assertThat(send("GET", "/api/organizations/" + org + "/members", null, organizer).body().get(0)
                .path("userId").asString()).isEqualTo(otherOrganizer.id().toString());
        assertThat(send("DELETE", "/api/organizations/" + org + "/members/" + otherOrganizer.id(), null,
                otherOrganizer).code()).isEqualTo("OWNER_CANNOT_LEAVE");
        assertThat(send("PUT", "/api/organizations/" + org, Map.of("name", "Đổi tên"), organizer).code())
                .isEqualTo("NOT_ORGANIZATION_OWNER");
    }

    private List<String> checkInEventIds(Caller caller) {
        return send("GET", "/api/events/check-in", null, caller).body().valueStream()
                .map(event -> event.path("id").asString()).toList();
    }

    @Test
    void checkInListShowsPublishedEventsOfHostStaffOwnerAndManager() {
        String org = createOrganization(organizer);
        invite(org, otherOrganizer, "MANAGER", organizer);
        invite(org, member, "MEMBER", organizer);
        Map<String, Object> body = futureEvent("Soát vé CLB", "Huế");
        body.put("organizationId", org);
        String published = publish(organizer, body);
        String draft = createDraft(organizer, body);
        send("POST", "/api/events/" + published + "/staff", Map.of("email", email(attendee)), organizer);

        for (Caller caller : List.of(organizer, otherOrganizer, attendee)) {
            assertThat(checkInEventIds(caller)).contains(published).doesNotContain(draft);
        }
        assertThat(checkInEventIds(member)).doesNotContain(published);
        assertThat(checkInEventIds(outsider)).doesNotContain(published);
    }

    @Test
    void staffCanViewAndCheckInButNotManage() {
        Map<String, Object> body = futureEvent("Có staff", "Huế");
        String id = createDraft(organizer, body);
        Caller staff = attendee;

        assertThat(send("POST", "/api/events/" + id + "/staff", Map.of("email", email(staff)), outsider).code())
                .isEqualTo("NOT_EVENT_OWNER");
        Response added = send("POST", "/api/events/" + id + "/staff", Map.of("email", email(staff)), organizer);
        assertThat(added.status()).isEqualTo(201);
        assertThat(added.text("fullName")).startsWith("Người dùng");
        assertThat(send("GET", "/api/events/" + id + "/staff", null, organizer).body().get(0).path("userId").asString())
                .isEqualTo(staff.id().toString());

        assertThat(send("GET", "/api/events/" + id, null, staff).status()).isEqualTo(200);
        assertThat(send("PUT", "/api/events/" + id, body, staff).status()).isEqualTo(403);
        Response access = access(id, staff);
        assertThat(access.body().path("canCheckIn").asBoolean()).isTrue();
        assertThat(access.body().path("canManage").asBoolean()).isFalse();

        send("DELETE", "/api/events/" + id + "/staff/" + staff.id(), null, organizer);
        assertThat(send("GET", "/api/events/" + id + "/staff", null, organizer).body().size()).isZero();
        assertThat(send("PUT", "/api/events/" + id + "/host", Map.of("userId", staff.id()), organizer).code())
                .isEqualTo("PERSONAL_EVENT");
    }

    @Test
    void onlyAdminManagesVenues() {
        Map<String, Object> venue = Map.of("name", "Phòng 201", "address", "1 Lê Duẩn", "city", "Huế");
        assertThat(send("POST", "/api/venues", venue, organizer).status()).isEqualTo(403);
        assertThat(send("POST", "/api/venues", venue, admin).status()).isEqualTo(201);
    }

    @Test
    void adminAndLockedAccountsCannotBeGivenEventRoles() {
        String org = createOrganization(organizer);
        assertThat(invite(org, admin, "MEMBER", organizer).code()).isEqualTo("USER_IS_ADMIN");
        locked.add(outsider.id());
        assertThat(invite(org, outsider, "MEMBER", organizer).code()).isEqualTo("USER_LOCKED");

        String staff = "/api/events/" + createDraft(organizer, futureEvent("Không giao quyền", "Huế")) + "/staff";
        assertThat(send("POST", staff, Map.of("email", email(admin)), organizer).code()).isEqualTo("USER_IS_ADMIN");
        assertThat(send("POST", staff, Map.of("email", email(outsider)), organizer).code()).isEqualTo("USER_LOCKED");

        // Thành viên bị khóa sau khi vào tổ chức thì không nhận làm HOST được.
        assertThat(invite(org, member, "MEMBER", organizer).status()).isEqualTo(201);
        Map<String, Object> body = futureEvent("Sự kiện tổ chức", "Huế");
        body.put("organizationId", org);
        String id = createDraft(organizer, body);
        locked.add(member.id());
        assertThat(send("PUT", "/api/events/" + id + "/host", Map.of("userId", member.id()), organizer).code())
                .isEqualTo("USER_LOCKED");
    }

    @Test
    void cancelledEventKeepsItsTeam() {
        String id = publish(organizer, futureEvent("Đã hủy thì thôi", "Huế"));
        String staff = "/api/events/" + id + "/staff";
        assertThat(send("POST", staff, Map.of("email", email(attendee)), organizer).status()).isEqualTo(201);
        send("PUT", "/api/events/" + id + "/cancel", null, organizer);

        assertThat(send("POST", staff, Map.of("email", email(outsider)), organizer).code())
                .isEqualTo("EVENT_STATUS_INVALID");
        assertThat(send("DELETE", staff + "/" + attendee.id(), null, organizer).code()).isEqualTo("EVENT_STATUS_INVALID");
    }

    @Test
    void coverImageIsCheckedThenUploadedToTheEventFolder() {
        when(images.upload(any(), anyString())).thenReturn(
                new ImageStorage.UploadedImage("https://res.cloudinary.com/demo/cover.png", "openevent/events/x/cover"));
        String id = createDraft(organizer, futureEvent("Có ảnh bìa", "Huế"));

        Response uploaded = upload(id, "image/png", PNG, organizer);
        assertThat(uploaded.status()).isEqualTo(201);
        assertThat(uploaded.text("url")).isEqualTo("https://res.cloudinary.com/demo/cover.png");
        verify(images).upload(any(), eq("openevent/events/" + id));

        assertThat(upload(id, "image/png", "không phải ảnh".getBytes(StandardCharsets.UTF_8), organizer).code())
                .isEqualTo("IMAGE_CONTENT_INVALID");
        assertThat(upload(id, "image/gif", PNG, organizer).code()).isEqualTo("IMAGE_TYPE_NOT_SUPPORTED");
        assertThat(upload(id, "image/png", PNG, otherOrganizer).code()).isEqualTo("NOT_EVENT_OWNER");

        send("PUT", "/api/events/" + id + "/submit", null, organizer);
        assertThat(upload(id, "image/png", PNG, organizer).code()).isEqualTo("EVENT_STATUS_INVALID");
    }
}
