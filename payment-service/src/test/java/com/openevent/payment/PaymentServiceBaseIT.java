package com.openevent.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;

import com.openevent.common.messaging.Messaging;

/** Khung service chạy được trên hạ tầng thật: database, RabbitMQ, bảo mật từ common-lib. */
class PaymentServiceBaseIT extends PaymentIntegrationTest {

    @Test
    void healthIsUpWithDatabaseAndBroker() {
        Response response = send("GET", "/actuator/health", null, null);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.text("status")).isEqualTo("UP");
    }

    @Test
    void apiRequiresIdentityFromGatewayHeaders() {
        String path = "/api/payments/" + UUID.randomUUID();

        assertThat(send("GET", path, null, null).status()).isEqualTo(401);
        assertThat(send("GET", path, null, buyer).code()).isEqualTo("PAYMENT_NOT_FOUND");
    }

    @Test
    void openApiDocumentIsAvailable() {
        assertThat(send("GET", "/v3/api-docs", null, null).status()).isEqualTo(200);
    }

    @Test
    void flywayCreatesSchemaOfThisService() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertThat(tables).contains("payments", "payment_logs", "processed_messages");
    }

    @Test
    void queueAndDeadLetterQueueAreDeclared() {
        assertThat(amqp.getQueueProperties("payment-service.events")).isNotNull();
        assertThat(amqp.getQueueProperties("payment-service.events.dlq")).isNotNull();
    }

    @Test
    void unreadableMessageGoesToDeadLetterQueueInsteadOfLoopingForever() {
        rabbit.send(Messaging.EXCHANGE, Messaging.ORDER_REFUND_REQUESTED,
                MessageBuilder.withBody("not json".getBytes()).build());

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(amqp.getQueueInfo("payment-service.events.dlq").getMessageCount()).isPositive());
        assertThat(amqp.purgeQueue("payment-service.events.dlq")).isPositive();
    }
}
