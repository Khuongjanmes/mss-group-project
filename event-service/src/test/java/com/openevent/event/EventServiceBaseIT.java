package com.openevent.event;

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
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.time.Duration;
import java.time.Instant;
import com.openevent.common.messaging.Messaging;
import com.openevent.common.messaging.events.EventCancelled;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Khung service chạy được trên hạ tầng thật: database, RabbitMQ, bảo mật từ common-lib. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"eureka.client.enabled=false"})
class EventServiceBaseIT {

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

    @Autowired
    TopicExchange exchange;

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
    void publicReadNeedsNoIdentity() throws Exception {
        assertThat(get("/api/events").statusCode()).isEqualTo(200);
    }

    @Test
    void roleGuardedReadUsesIdentityFromGatewayHeaders() throws Exception {
        String userId = UUID.randomUUID().toString();

        assertThat(get("/api/events/my").statusCode()).isEqualTo(401);
        assertThat(get("/api/events/my", "X-User-Id", userId, "X-User-Roles", "ADMIN").statusCode()).isEqualTo(403);
        assertThat(get("/api/events/my", "X-User-Id", userId, "X-User-Roles", "ATTENDEE").statusCode()).isEqualTo(200);
    }

    @Test
    void openApiDocumentIsAvailable() throws Exception {
        assertThat(get("/v3/api-docs").statusCode()).isEqualTo(200);
    }

    @Test
    void flywayCreatesSchemaOfThisService() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertThat(tables).contains("categories", "venues", "events");
    }

    @Test
    void publishesEventCancelledAsJsonToSharedExchange() {
        Queue probe = QueueBuilder.nonDurable("event-probe").autoDelete().build();
        admin.declareQueue(probe);
        admin.declareBinding(BindingBuilder.bind(probe).to(exchange).with(Messaging.EVENT_CANCELLED));
        EventCancelled event = new EventCancelled(UUID.randomUUID(), Instant.now(), UUID.randomUUID());

        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.EVENT_CANCELLED, event);

        Object received = rabbit.receiveAndConvert("event-probe", Duration.ofSeconds(10).toMillis());
        assertThat(received).isEqualTo(event);
    }
}
