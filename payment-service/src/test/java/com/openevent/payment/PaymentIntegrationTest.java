package com.openevent.payment;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

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
import com.openevent.payment.client.OrderClient;
import com.openevent.payment.client.OrderClient.OrderSummary;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Hạ tầng thật (PostgreSQL, RabbitMQ) dùng chung cho các test; Order được giả lập. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"eureka.client.enabled=false"})
abstract class PaymentIntegrationTest {

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

    record Response(int status, JsonNode body, String location) {
        String code() {
            return body.path("code").asString();
        }

        String text(String field) {
            return body.path(field).asString();
        }
    }

    protected final Caller buyer = new Caller(UUID.randomUUID(), "USER");
    protected final Caller stranger = new Caller(UUID.randomUUID(), "USER");
    protected final Caller admin = new Caller(UUID.randomUUID(), "USER,ADMIN");

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
    OrderClient orders;

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
            return new Response(response.statusCode(), node, response.headers().firstValue("Location").orElse(null));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Đơn PENDING của {@code owner}, còn hạn 10 phút, mà Order giả trả về. */
    protected UUID pendingOrder(Caller owner, String amount) {
        UUID orderId = UUID.randomUUID();
        org.mockito.Mockito.when(orders.getOrder(orderId)).thenReturn(new OrderSummary(orderId, owner.id(),
                new BigDecimal(amount), "PENDING", Instant.now().plusSeconds(600)));
        return orderId;
    }

    /** Queue tạm nhận message theo routing key, để kiểm tra service đã phát. */
    protected String probe(String name, String routingKey) {
        // Không autoDelete: queue bị xóa sau lần nhận đầu, test nhận nhiều lần.
        Queue queue = QueueBuilder.nonDurable(name).build();
        amqp.declareQueue(queue);
        amqp.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange(Messaging.EXCHANGE)).with(routingKey));
        amqp.purgeQueue(name);
        return name;
    }
}
