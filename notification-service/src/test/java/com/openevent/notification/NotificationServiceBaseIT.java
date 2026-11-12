package com.openevent.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.bson.Document;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.awaitility.Awaitility;
import java.time.Duration;
import java.time.Instant;
import com.openevent.common.messaging.Messaging;
import com.openevent.common.messaging.events.TicketIssued;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.mongodb.MongoDBContainer;

/** Khung service chạy được trên hạ tầng thật: database, RabbitMQ, bảo mật từ common-lib. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"eureka.client.enabled=false",
        // Message TicketIssued ở đây không gọi được Identity; không để nó giữ consumer 80 giây như cấu hình thật.
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.multiplier=1"})
class NotificationServiceBaseIT {

    static final MongoDBContainer DB = new MongoDBContainer("mongo:7");

    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    static {
        DB.start();
        RABBIT.start();
    }

    /** Đi qua đúng biến môi trường và khóa cấu hình trong application.yml như khi chạy bằng Docker Compose. */
    @DynamicPropertySource
    static void mongoUri(DynamicPropertyRegistry registry) {
        registry.add("MONGODB_URI", () -> DB.getConnectionString() + "/notification_db");
    }

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    MongoTemplate mongo;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    AmqpAdmin admin;

    private HttpResponse<String> get(String path, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (headers.length > 0) {
            request.headers(headers);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthIsUpWithDatabaseAndBroker() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void pingFollowsSecurityRules() throws Exception {
        assertThat(get("/api/notifications/ping").statusCode()).isEqualTo(401);
    }

    @Test
    void pingReceivesIdentityFromGatewayHeaders() throws Exception {
        String userId = UUID.randomUUID().toString();

        HttpResponse<String> response = get("/api/notifications/ping", "X-User-Id", userId, "X-User-Roles", "USER");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("notification-service", userId, "USER");
    }

    @Test
    void openApiDocumentIsAvailable() throws Exception {
        assertThat(get("/v3/api-docs").statusCode()).isEqualTo(200);
    }

    @Test
    void mongoIsReadableAndWritable() {
        mongo.save(new Document("probe", "ok"), "base_probe");

        assertThat(mongo.count(new org.springframework.data.mongodb.core.query.Query(), "base_probe")).isPositive();
        mongo.dropCollection("base_probe");
    }

    @Test
    void queueAndDeadLetterQueueAreDeclared() {
        assertThat(admin.getQueueProperties("notification-service.events")).isNotNull();
        assertThat(admin.getQueueProperties("notification-service.events.dlq")).isNotNull();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void listenerReceivesEventPublishedToExchange(CapturedOutput output) {
        TicketIssued event = new TicketIssued(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), java.util.List.of("code"));

        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.TICKET_ISSUED, event);

        Awaitility.await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(output).contains("Nhận TicketIssued: " + event.messageId()));
    }

    @Test
    void unreadableMessageGoesToDeadLetterQueueInsteadOfLoopingForever() {
        rabbit.send(Messaging.EXCHANGE, Messaging.TICKET_ISSUED, MessageBuilder.withBody("not json".getBytes()).build());

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(admin.getQueueInfo("notification-service.events.dlq").getMessageCount()).isPositive());
        assertThat(admin.purgeQueue("notification-service.events.dlq")).isPositive();
    }
}
