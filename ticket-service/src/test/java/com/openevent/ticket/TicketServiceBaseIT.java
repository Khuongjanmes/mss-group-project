package com.openevent.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;

import com.openevent.common.messaging.Messaging;

/** Khung service chạy được trên hạ tầng thật: database, RabbitMQ, bảo mật từ common-lib. */
class TicketServiceBaseIT extends TicketIntegrationTest {

    @Test
    void healthIsUpWithDatabaseAndBroker() {
        Response response = send("GET", "/actuator/health", null, null);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.text("status")).isEqualTo("UP");
    }

    @Test
    void readingTicketTypesIsPublicButTicketsNeedIdentity() {
        assertThat(send("GET", "/api/ticket-types?eventId=" + UUID.randomUUID(), null, null).status()).isEqualTo(200);
        assertThat(send("GET", "/api/tickets/my", null, null).status()).isEqualTo(401);
        assertThat(send("GET", "/api/tickets/my", null, buyer).status()).isEqualTo(200);
    }

    @Test
    void openApiDocumentIsAvailable() {
        assertThat(send("GET", "/v3/api-docs", null, null).status()).isEqualTo(200);
    }

    @Test
    void flywayCreatesSchemaOfThisService() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertThat(tables).contains("ticket_types", "reservations", "tickets", "processed_messages");
    }

    @Test
    void queueAndDeadLetterQueueAreDeclared() {
        assertThat(amqp.getQueueProperties("ticket-service.events")).isNotNull();
        assertThat(amqp.getQueueProperties("ticket-service.events.dlq")).isNotNull();
    }

    @Test
    void unreadableMessageGoesToDeadLetterQueueInsteadOfLoopingForever() {
        rabbit.send(Messaging.EXCHANGE, Messaging.ORDER_PAID, MessageBuilder.withBody("not json".getBytes()).build());

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(amqp.getQueueInfo("ticket-service.events.dlq").getMessageCount()).isPositive());
        assertThat(amqp.purgeQueue("ticket-service.events.dlq")).isPositive();
    }
}
