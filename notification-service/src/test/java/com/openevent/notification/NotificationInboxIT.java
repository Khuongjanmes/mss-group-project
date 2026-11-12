package com.openevent.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.openevent.notification.notifications.Notification;
import com.openevent.notification.notifications.NotificationChannel;
import com.openevent.notification.notifications.NotificationRepository;
import com.openevent.notification.notifications.NotificationType;

/** Hộp thông báo (#62) qua HTTP như Gateway gọi: danh tính người gọi lấy từ header X-User-Id. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"eureka.client.enabled=false"})
class NotificationInboxIT {

    static final MongoDBContainer DB = new MongoDBContainer("mongo:7");

    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    static {
        DB.start();
        RABBIT.start();
    }

    @DynamicPropertySource
    static void mongoUri(DynamicPropertyRegistry registry) {
        registry.add("MONGODB_URI", () -> DB.getConnectionString() + "/notification_db");
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    MongoTemplate mongo;

    private final UUID me = UUID.randomUUID();
    private final UUID someoneElse = UUID.randomUUID();

    @BeforeEach
    void clear() {
        mongo.remove(new Query(), Notification.class);
    }

    @Test
    void listsOnlyMyNotificationsNewestFirstWithUnreadCount() throws Exception {
        save(me, "Vé của bạn: A", "2026-11-10T10:00:00Z");
        save(me, "Vé của bạn: B", "2026-11-12T10:00:00Z");
        save(me, "Sự kiện đã bị hủy: C", "2026-11-11T10:00:00Z");
        save(someoneElse, "Vé của người khác", "2026-11-13T10:00:00Z");

        HttpResponse<String> response = send("GET", "/api/notifications/my?page=0&size=2", me);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("content")).hasSize(2);
        assertThat(body.get("content").get(0).get("title").asText()).isEqualTo("Vé của bạn: B");
        assertThat(body.get("content").get(1).get("title").asText()).isEqualTo("Sự kiện đã bị hủy: C");
        assertThat(body.get("content").get(0).get("read").asBoolean()).isFalse();
        assertThat(body.get("totalElements").asLong()).isEqualTo(3);
        assertThat(body.get("totalPages").asInt()).isEqualTo(2);
        assertThat(body.get("unreadCount").asLong()).isEqualTo(3);
    }

    @Test
    void markingReadLowersUnreadCountAndIsIdempotent() throws Exception {
        Notification first = save(me, "Vé của bạn: A", "2026-11-10T10:00:00Z");
        save(me, "Vé của bạn: B", "2026-11-11T10:00:00Z");

        assertThat(send("PATCH", "/api/notifications/" + first.getId() + "/read", me).statusCode()).isEqualTo(204);
        assertThat(send("PATCH", "/api/notifications/" + first.getId() + "/read", me).statusCode()).isEqualTo(204);

        HttpResponse<String> count = send("GET", "/api/notifications/my/unread-count", me);
        assertThat(count.statusCode()).isEqualTo(200);
        assertThat(json.readTree(count.body()).get("unreadCount").asLong()).isEqualTo(1);
        assertThat(notifications.findById(first.getId())).hasValueSatisfying(n -> assertThat(n.isRead()).isTrue());
    }

    @Test
    void someoneElsesNotificationIsNotFoundAndStaysUnread() throws Exception {
        Notification theirs = save(someoneElse, "Vé của người khác", "2026-11-10T10:00:00Z");

        HttpResponse<String> response = send("PATCH", "/api/notifications/" + theirs.getId() + "/read", me);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo("NOTIFICATION_NOT_FOUND");
        assertThat(notifications.findById(theirs.getId())).hasValueSatisfying(n -> assertThat(n.isRead()).isFalse());
        assertThat(send("PATCH", "/api/notifications/" + UUID.randomUUID() + "/read", me).statusCode()).isEqualTo(404);
    }

    @Test
    void requiresLoginAndValidPaging() throws Exception {
        assertThat(send("GET", "/api/notifications/my/unread-count", null).statusCode()).isEqualTo(401);
        assertThat(send("PATCH", "/api/notifications/abc/read", null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/notifications/my?size=101", me).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/notifications/my?page=-1", me).statusCode()).isEqualTo(400);
    }

    private Notification save(UUID userId, String title, String sentAt) {
        return notifications.save(Notification.sent(UUID.randomUUID(), userId, NotificationType.TICKET_ISSUED, title,
                "nội dung", NotificationChannel.EMAIL, Instant.parse(sentAt)));
    }

    private HttpResponse<String> send(String method, String path, UUID caller) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (caller != null) {
            request.header("X-User-Id", caller.toString()).header("X-User-Roles", "USER");
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
