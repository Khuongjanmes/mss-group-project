package com.openevent.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
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
import com.openevent.common.messaging.events.OrderRefundRequested;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Khung service chạy được trên hạ tầng thật: database, RabbitMQ, bảo mật từ common-lib. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"eureka.client.enabled=false"})
class PaymentServiceBaseIT {

    @ServiceConnection
    static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:16-alpine");

    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    static {
        DB.start();
        RABBIT.start();
    }

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

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
        assertThat(get("/api/payments/ping").statusCode()).isEqualTo(401);
    }

    @Test
    void pingReceivesIdentityFromGatewayHeaders() throws Exception {
        String userId = UUID.randomUUID().toString();

        HttpResponse<String> response = get("/api/payments/ping", "X-User-Id", userId, "X-User-Roles", "ATTENDEE");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("payment-service", userId, "ATTENDEE");
    }

    @Test
    void openApiDocumentIsAvailable() throws Exception {
        assertThat(get("/v3/api-docs").statusCode()).isEqualTo(200);
    }

    @Test
    void flywayCreatesSchemaOfThisService() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertThat(tables).contains("payments", "payment_logs", "processed_messages");
    }

    @Test
    void queueAndDeadLetterQueueAreDeclared() {
        assertThat(admin.getQueueProperties("payment-service.events")).isNotNull();
        assertThat(admin.getQueueProperties("payment-service.events.dlq")).isNotNull();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void listenerReceivesEventPublishedToExchange(CapturedOutput output) {
        OrderRefundRequested event = new OrderRefundRequested(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), "late");

        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.ORDER_REFUND_REQUESTED, event);

        Awaitility.await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(output).contains("Nhận OrderRefundRequested: " + event.messageId()));
    }

    @Test
    void unreadableMessageGoesToDeadLetterQueueInsteadOfLoopingForever() {
        rabbit.send(Messaging.EXCHANGE, Messaging.ORDER_REFUND_REQUESTED, MessageBuilder.withBody("not json".getBytes()).build());

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(admin.getQueueInfo("payment-service.events.dlq").getMessageCount()).isPositive());
        assertThat(admin.purgeQueue("payment-service.events.dlq")).isPositive();
    }
}
