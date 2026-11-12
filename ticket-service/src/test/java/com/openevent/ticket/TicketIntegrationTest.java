package com.openevent.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
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
import com.openevent.ticket.client.EventClient;
import com.openevent.ticket.client.EventClient.EventAccess;
import com.openevent.ticket.client.EventClient.EventSummary;
import com.openevent.ticket.client.IdentityClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Hạ tầng thật (PostgreSQL, RabbitMQ); Event và Identity được giả lập. Job nhả vé tắt, test gọi trực tiếp. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "openevent.ticket.release-interval=3600000"})
abstract class TicketIntegrationTest {

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

    /** Sự kiện mà Event giả trả về. {@code staff} và {@code managers} đổi được trong test. */
    record FakeEvent(UUID id, UUID organizerId, Set<UUID> managers, Set<UUID> staff, Instant startTime,
            Instant endTime) {
    }

    protected final Caller host = new Caller(UUID.randomUUID(), "USER");
    protected final Caller buyer = new Caller(UUID.randomUUID(), "USER");
    protected final Caller stranger = new Caller(UUID.randomUUID(), "USER");

    /** Sự kiện → Event giả; {@code null} trong {@link #eventDown} nghĩa là Event không phản hồi. */
    protected final Map<UUID, FakeEvent> fakeEvents = new ConcurrentHashMap<>();
    protected volatile boolean eventDown;

    private final HttpClient http = HttpClient.newHttpClient();

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

    @MockitoBean
    EventClient events;

    @MockitoBean
    IdentityClient identity;

    @BeforeEach
    void fakeEventService() {
        when(events.getEvent(any())).thenAnswer(call -> {
            FakeEvent event = find(call.getArgument(0));
            return new EventSummary(event.id(), event.organizerId(), "PUBLISHED", event.startTime(), event.endTime());
        });
        when(events.getAccess(any(), any())).thenAnswer(call -> {
            FakeEvent event = find(call.getArgument(0));
            UUID user = call.getArgument(1);
            boolean manage = event.organizerId().equals(user) || event.managers().contains(user);
            return new EventAccess(manage, manage || event.staff().contains(user));
        });
        when(identity.getUsers(any())).thenAnswer(call -> ((IdentityClient.UserBatchRequest) call.getArgument(0)).ids()
                .stream().map(id -> new IdentityClient.UserContact(id, id + "@test.local", "Người " + id.toString()
                        .substring(0, 4), null, "ACTIVE")).toList());
    }

    private FakeEvent find(UUID id) {
        if (eventDown) {
            throw new IllegalStateException("event-service down");
        }
        FakeEvent event = fakeEvents.get(id);
        if (event == null) {
            throw new feign.FeignException.NotFound("không có", feign.Request.create(feign.Request.HttpMethod.GET,
                    "/", Map.of(), null, java.nio.charset.StandardCharsets.UTF_8, null), null, Map.of());
        }
        return event;
    }

    /** Sự kiện của {@link #host} bắt đầu sau {@code startsIn}, kéo dài 3 giờ. */
    protected UUID event(Duration startsIn) {
        UUID id = UUID.randomUUID();
        Instant start = Instant.now().plus(startsIn);
        fakeEvents.put(id, new FakeEvent(id, host.id(), ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet(),
                start, start.plus(Duration.ofHours(3))));
        return id;
    }

    protected Map<String, Object> typeBody(UUID eventId, String name, int quantity, String price, Instant saleEnd) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("name", name);
        body.put("price", new BigDecimal(price));
        body.put("totalQuantity", quantity);
        body.put("saleStart", Instant.now().minus(Duration.ofHours(1)));
        body.put("saleEnd", saleEnd);
        return body;
    }

    /** Loại vé đang mở bán, bán tới 1 giờ trước khi sự kiện bắt đầu. */
    protected String createType(UUID eventId, String name, int quantity, String price) {
        Instant saleEnd = fakeEvents.get(eventId).startTime().minus(Duration.ofHours(1));
        Response created = send("POST", "/api/ticket-types", typeBody(eventId, name, quantity, price, saleEnd), host);
        assertThat(created.status()).isEqualTo(201);
        return created.text("id");
    }

    protected Response reserve(UUID orderId, Map<String, Integer> quantities) {
        List<Map<String, Object>> items = quantities.entrySet().stream()
                .map(e -> Map.<String, Object>of("ticketTypeId", e.getKey(), "quantity", e.getValue())).toList();
        return send("POST", "/internal/reservations", Map.of("orderId", orderId, "items", items), null);
    }

    protected JsonNode type(String id) {
        return send("GET", "/api/ticket-types/" + id, null, null).body();
    }

    protected Response send(String method, String path, Object body, Caller caller) {
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

    /** Queue tạm nhận message theo routing key; không autoDelete vì test nhận nhiều lần. */
    protected String probe(String name, String routingKey) {
        Queue queue = QueueBuilder.nonDurable(name).build();
        amqp.declareQueue(queue);
        amqp.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange(Messaging.EXCHANGE)).with(routingKey));
        amqp.purgeQueue(name);
        return name;
    }
}
