package com.openevent.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import com.openevent.common.messaging.Messaging;
import com.openevent.common.messaging.events.OrderCancelled;
import com.openevent.order.client.EventClient;
import com.openevent.order.client.EventClient.EventSummary;
import com.openevent.order.client.FeignErrors;
import com.openevent.order.client.IdentityClient;
import com.openevent.order.client.IdentityClient.UserContact;
import com.openevent.order.client.TicketClient;
import com.openevent.order.client.TicketClient.ReservationRequest;
import com.openevent.order.client.TicketClient.ReservationResponse;
import com.openevent.order.client.TicketClient.ReservedItem;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * KB-1 trên hạ tầng thật (PostgreSQL, RabbitMQ). Identity, Event, Ticket được giả lập: Ticket giả trả đơn giá
 * 1.000.000đ cho mọi loại vé của {@link #eventId}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "openevent.order.remote.wait=0ms"})
class OrderApiIT {

    @ServiceConnection
    static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:16-alpine");

    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    static {
        DB.start();
        RABBIT.start();
    }

    private static final String CANCELLED_PROBE = "order-api-it.order-cancelled";
    private static final BigDecimal PRICE = new BigDecimal("1000000");

    record Response(int status, JsonNode body) {
        String code() {
            return body.path("code").asString();
        }
    }

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
    IdentityClient identity;

    @MockitoBean
    EventClient events;

    @MockitoBean
    TicketClient tickets;

    private final HttpClient http = HttpClient.newHttpClient();
    private final UUID userId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID vip = UUID.randomUUID();
    private final UUID standard = UUID.randomUUID();

    @BeforeEach
    void fakeOtherServices() {
        when(identity.getUser(any())).thenAnswer(call -> new UserContact(call.getArgument(0), "u@test.local",
                "Người mua", null, "ACTIVE"));
        when(events.getEvent(any())).thenAnswer(call -> new EventSummary(call.getArgument(0), UUID.randomUUID(),
                "PUBLISHED", Instant.now().plusSeconds(86_400), Instant.now().plusSeconds(90_000)));
        when(tickets.reserve(any())).thenAnswer(call -> holdAll(call.getArgument(0), eventId));

        Queue probe = new Queue(CANCELLED_PROBE, false, false, true);
        amqp.declareQueue(probe);
        Binding binding = BindingBuilder.bind(probe).to(new TopicExchange(Messaging.EXCHANGE))
                .with(Messaging.ORDER_CANCELLED);
        amqp.declareBinding(binding);
        amqp.purgeQueue(CANCELLED_PROBE);
    }

    private static ReservationResponse holdAll(ReservationRequest request, UUID ofEvent) {
        return new ReservationResponse(request.orderId(), ofEvent, Instant.now().plusSeconds(600),
                request.items().stream().map(item -> new ReservedItem(item.ticketTypeId(), item.quantity(), PRICE))
                        .toList());
    }

    private Map<String, Object> order(Object... ticketTypeAndQuantity) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < ticketTypeAndQuantity.length; i += 2) {
            items.add(Map.of("ticketTypeId", ticketTypeAndQuantity[i], "quantity", ticketTypeAndQuantity[i + 1]));
        }
        return Map.of("eventId", eventId, "items", items);
    }

    private Response post(UUID caller, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (caller != null) {
            request.header("X-User-Id", caller.toString()).header("X-User-Roles", "USER");
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(),
                response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body()));
    }

    private Integer quota(UUID user) {
        List<Integer> rows = jdbc.queryForList(
                "select quantity from user_event_quotas where user_id = ? and event_id = ?", Integer.class, user,
                eventId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private String status(String orderId) {
        return jdbc.queryForObject("select status from orders where id = ?", String.class, UUID.fromString(orderId));
    }

    private OrderCancelled receiveCancelled() {
        return (OrderCancelled) rabbit.receiveAndConvert(CANCELLED_PROBE, 10_000);
    }

    @Test
    void createsPendingOrderPricedByTicket() throws Exception {
        Response created = post(userId, order(vip, 2, standard, 1));

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().path("status").asString()).isEqualTo("PENDING");
        assertThat(created.body().path("eventId").asString()).isEqualTo(eventId.toString());
        assertThat(created.body().path("totalAmount").decimalValue()).isEqualByComparingTo("3000000");
        assertThat(created.body().path("items")).hasSize(2);
        assertThat(Instant.parse(created.body().path("expiresAt").asString()))
                .isBefore(Instant.now().plusSeconds(601));

        String id = created.body().path("id").asString();
        assertThat(status(id)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from order_items where order_id = ?", Integer.class,
                UUID.fromString(id))).isEqualTo(2);
        assertThat(quota(userId)).isEqualTo(3);
    }

    @Test
    void sixthTicketOfTheSameEventIsRejected() throws Exception {
        assertThat(post(userId, order(vip, 3)).status()).isEqualTo(201);

        Response second = post(userId, order(standard, 3));
        assertThat(second.status()).isEqualTo(409);
        assertThat(second.code()).isEqualTo("QUOTA_EXCEEDED");
        assertThat(quota(userId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from orders where user_id = ?", Integer.class, userId))
                .isEqualTo(1);
    }

    @Test
    void concurrentOrdersNeverExceedFiveTicketsPerUser() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<Integer>> attempts = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                attempts.add(() -> post(userId, order(UUID.randomUUID(), 1)).status());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : pool.invokeAll(attempts)) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(code -> code == 201).hasSize(5);
            assertThat(statuses).filteredOn(code -> code == 409).hasSize(5);
        } finally {
            pool.shutdown();
        }
        assertThat(quota(userId)).isEqualTo(5);
    }

    @Test
    void soldOutCancelsOrderAndGivesQuotaBack() throws Exception {
        doThrow(FeignErrors.status(409,
                "{\"status\":409,\"code\":\"SOLD_OUT\",\"message\":\"Loại vé \\\"VIP\\\" không còn đủ 2 vé\"}"))
                .when(tickets).reserve(any());

        Response response = post(userId, order(vip, 2));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.code()).isEqualTo("SOLD_OUT");
        assertThat(jdbc.queryForObject("select status from orders where user_id = ?", String.class, userId))
                .isEqualTo("CANCELLED");
        assertThat(quota(userId)).isZero();
    }

    @Test
    void ticketTypesOfAnotherEventAreRejectedAndTicketIsToldToRelease() throws Exception {
        UUID otherEvent = UUID.randomUUID();
        doAnswer(call -> holdAll(call.getArgument(0), otherEvent)).when(tickets).reserve(any());

        Response response = post(userId, order(vip, 1));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.code()).isEqualTo("TICKET_EVENT_MISMATCH");
        String orderId = jdbc.queryForObject("select id from orders where user_id = ?", String.class, userId);
        assertThat(status(orderId)).isEqualTo("CANCELLED");
        assertThat(quota(userId)).isZero();
        OrderCancelled cancelled = receiveCancelled();
        assertThat(cancelled).isNotNull();
        assertThat(cancelled.orderId()).hasToString(orderId);
        assertThat(cancelled.reason()).isEqualTo("TICKET_EVENT_MISMATCH");
    }

    @Test
    void ticketDownCancelsOrderAndTicketIsToldToRelease() throws Exception {
        doThrow(new IllegalStateException("connection refused")).when(tickets).reserve(any());

        Response response = post(userId, order(vip, 1));

        assertThat(response.status()).isEqualTo(503);
        assertThat(response.code()).isEqualTo("SERVICE_BUSY");
        OrderCancelled cancelled = receiveCancelled();
        assertThat(cancelled).isNotNull();
        assertThat(cancelled.reason()).isEqualTo("TICKET_UNAVAILABLE");
        assertThat(quota(userId)).isZero();
    }

    @Test
    void requiresLoginAndValidBody() throws Exception {
        assertThat(post(null, order(vip, 1)).status()).isEqualTo(401);
        assertThat(post(userId, order(vip, 0)).status()).isEqualTo(400);
        assertThat(post(userId, Map.of("items", List.of())).status()).isEqualTo(400);
    }
}
