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
import com.openevent.common.messaging.events.EventCancelled;
import com.openevent.common.messaging.events.OrderCancelled;
import com.openevent.common.messaging.events.OrderPaid;
import com.openevent.common.messaging.events.OrderRefundRequested;
import com.openevent.common.messaging.events.PaymentFailed;
import com.openevent.common.messaging.events.PaymentSucceeded;
import com.openevent.order.client.EventClient;
import com.openevent.order.client.EventClient.EventSummary;
import com.openevent.order.client.FeignErrors;
import com.openevent.order.client.IdentityClient;
import com.openevent.order.client.IdentityClient.UserContact;
import com.openevent.order.client.TicketClient;
import com.openevent.order.client.TicketClient.ReservationRequest;
import com.openevent.order.client.TicketClient.ReservationResponse;
import com.openevent.order.client.TicketClient.ReservedItem;
import com.openevent.order.orders.OrderStore;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * KB-1 trên hạ tầng thật (PostgreSQL, RabbitMQ). Identity, Event, Ticket được giả lập: Ticket giả trả đơn giá
 * 1.000.000đ cho mọi loại vé của {@link #eventId}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "openevent.order.remote.wait=0ms",
        "openevent.order.expire-interval=3600000"})
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
    private static final String PAID_PROBE = "order-api-it.order-paid";
    private static final String REFUND_PROBE = "order-api-it.order-refund-requested";
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

    @Autowired
    OrderStore store;

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

        probe(CANCELLED_PROBE, Messaging.ORDER_CANCELLED);
        probe(PAID_PROBE, Messaging.ORDER_PAID);
        probe(REFUND_PROBE, Messaging.ORDER_REFUND_REQUESTED);
    }

    /** Queue nghe message Order phát. Không auto-delete: test đọc nhiều message liên tiếp từ cùng queue. */
    private void probe(String queue, String routingKey) {
        Queue probe = new Queue(queue, false, false, false);
        amqp.declareQueue(probe);
        Binding binding = BindingBuilder.bind(probe).to(new TopicExchange(Messaging.EXCHANGE)).with(routingKey);
        amqp.declareBinding(binding);
        amqp.purgeQueue(queue);
    }

    /** Payment phát kết quả lên exchange như thật; Order nhận qua queue của mình. */
    private PaymentSucceeded paymentSucceeded(String orderId) {
        PaymentSucceeded event = new PaymentSucceeded(UUID.randomUUID(), Instant.now(), UUID.randomUUID(),
                UUID.fromString(orderId), new BigDecimal("1000000"), "TX-" + orderId);
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.PAYMENT_SUCCEEDED, event);
        return event;
    }

    private Object receive(String queue, long timeoutMillis) {
        return rabbit.receiveAndConvert(queue, timeoutMillis);
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
        return send("POST", "/api/orders", caller, body);
    }

    private Response get(UUID caller, String path) throws Exception {
        return send("GET", path, caller, null);
    }

    private Response cancel(UUID caller, String orderId) throws Exception {
        return send("PUT", "/api/orders/" + orderId + "/cancel", caller, null);
    }

    /** Đưa hạn đơn về quá khứ thay vì chờ 10 phút. */
    private void overdue(String orderId) {
        jdbc.update("update orders set expires_at = now() - interval '1 second' where id = ?",
                UUID.fromString(orderId));
    }

    private List<OrderCancelled> drainCancelled() {
        List<OrderCancelled> received = new ArrayList<>();
        for (OrderCancelled next = receiveCancelled(); next != null;
                next = (OrderCancelled) rabbit.receiveAndConvert(CANCELLED_PROBE, 1_000)) {
            received.add(next);
        }
        return received;
    }

    private Response send(String method, String path, UUID caller, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (caller != null) {
            request.header("X-User-Id", caller.toString()).header("X-User-Roles", "USER");
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(),
                response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body()));
    }

    /** Gọi không kèm header người dùng, như Payment gọi qua Feign. */
    private HttpResponse<String> getInternal(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Bản sao {@code OrderClient.OrderSummary} của payment-service: hợp đồng KB-2. */
    record PaymentOrderSummary(UUID id, UUID userId, BigDecimal totalAmount, String status, Instant expiresAt) {
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
    void paymentReadsAmountStatusAndExpiryOfAnOrder() throws Exception {
        Response created = post(userId, order(vip, 2));
        String id = created.body().path("id").asString();

        HttpResponse<String> response = getInternal("/internal/orders/" + id);

        assertThat(response.statusCode()).isEqualTo(200);
        PaymentOrderSummary summary = json.readValue(response.body(), PaymentOrderSummary.class);
        assertThat(summary.id()).hasToString(id);
        assertThat(summary.userId()).isEqualTo(userId);
        assertThat(summary.totalAmount()).isEqualByComparingTo("2000000");
        assertThat(summary.status()).isEqualTo("PENDING");
        assertThat(summary.expiresAt()).isEqualTo(Instant.parse(created.body().path("expiresAt").asString()));
    }

    @Test
    void unknownOrderIsNotFoundForPayment() throws Exception {
        HttpResponse<String> response = getInternal("/internal/orders/" + UUID.randomUUID());
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(json.readTree(response.body()).path("code").asString()).isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    void myOrdersAreNewestFirstAndOnlyMine() throws Exception {
        String first = post(userId, order(vip, 1)).body().path("id").asString();
        String second = post(userId, order(standard, 1)).body().path("id").asString();
        assertThat(post(UUID.randomUUID(), order(vip, 1)).status()).isEqualTo(201);

        Response page0 = get(userId, "/api/orders/my?size=1");
        assertThat(page0.status()).isEqualTo(200);
        assertThat(page0.body().path("totalElements").asLong()).isEqualTo(2);
        assertThat(page0.body().path("content").get(0).path("id").asString()).isEqualTo(second);
        assertThat(page0.body().path("content").get(0).path("items")).hasSize(1);

        Response page1 = get(userId, "/api/orders/my?size=1&page=1");
        assertThat(page1.body().path("content").get(0).path("id").asString()).isEqualTo(first);
        assertThat(page1.body().path("content").get(0).path("status").asString()).isEqualTo("PENDING");
    }

    @Test
    void myOrdersRequireLoginAndValidPaging() throws Exception {
        assertThat(get(null, "/api/orders/my").status()).isEqualTo(401);
        assertThat(get(userId, "/api/orders/my?size=0").status()).isEqualTo(400);
        assertThat(get(userId, "/api/orders/my?size=101").status()).isEqualTo(400);
    }

    @Test
    void ownerCancelsPendingOrderOnce() throws Exception {
        String id = post(userId, order(vip, 2)).body().path("id").asString();

        Response cancelled = cancel(userId, id);

        assertThat(cancelled.status()).isEqualTo(200);
        assertThat(cancelled.body().path("status").asString()).isEqualTo("CANCELLED");
        assertThat(status(id)).isEqualTo("CANCELLED");
        assertThat(quota(userId)).isZero();
        OrderCancelled event = receiveCancelled();
        assertThat(event).isNotNull();
        assertThat(event.orderId()).hasToString(id);
        assertThat(event.reason()).isEqualTo("USER_CANCELLED");

        Response again = cancel(userId, id);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("ORDER_NOT_PENDING");
        assertThat(quota(userId)).isZero();
    }

    @Test
    void othersCannotCancelMyOrder() throws Exception {
        String id = post(userId, order(vip, 1)).body().path("id").asString();

        Response response = cancel(UUID.randomUUID(), id);

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.code()).isEqualTo("ORDER_NOT_FOUND");
        assertThat(status(id)).isEqualTo("PENDING");
        assertThat(cancel(null, id).status()).isEqualTo(401);
    }

    @Test
    void unpaidOrderExpiresAndCanNoLongerBeCancelled() throws Exception {
        String overdueOrder = post(userId, order(vip, 2)).body().path("id").asString();
        String freshOrder = post(userId, order(standard, 1)).body().path("id").asString();
        overdue(overdueOrder);

        assertThat(store.expireDue(Instant.now(), 200)).isEqualTo(1);

        assertThat(status(overdueOrder)).isEqualTo("EXPIRED");
        assertThat(status(freshOrder)).isEqualTo("PENDING");
        assertThat(quota(userId)).isEqualTo(1);
        OrderCancelled event = receiveCancelled();
        assertThat(event).isNotNull();
        assertThat(event.orderId()).hasToString(overdueOrder);
        assertThat(event.reason()).isEqualTo("ORDER_EXPIRED");
        assertThat(cancel(userId, overdueOrder).code()).isEqualTo("ORDER_NOT_PENDING");
    }

    @Test
    void parallelExpiryJobsHandleEachOrderOnce() throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String id = post(UUID.randomUUID(), order(vip, 1)).body().path("id").asString();
            overdue(id);
            ids.add(id);
        }

        ExecutorService pool = Executors.newFixedThreadPool(4);
        int expired = 0;
        try {
            List<Callable<Integer>> instances = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                instances.add(() -> {
                    int total = 0;
                    for (int batch = store.expireDue(Instant.now(), 3); batch > 0;
                            batch = store.expireDue(Instant.now(), 3)) {
                        total += batch;
                    }
                    return total;
                });
            }
            for (Future<Integer> result : pool.invokeAll(instances)) {
                expired += result.get();
            }
        } finally {
            pool.shutdown();
        }

        assertThat(expired).isEqualTo(20);
        assertThat(ids).allSatisfy(id -> assertThat(status(id)).isEqualTo("EXPIRED"));
        assertThat(drainCancelled()).extracting(event -> event.orderId().toString())
                .containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    void paymentMarksOrderPaidOnceEvenIfMessageIsRedelivered() throws Exception {
        String id = post(userId, order(vip, 2)).body().path("id").asString();

        PaymentSucceeded payment = paymentSucceeded(id);
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.PAYMENT_SUCCEEDED, payment);

        assertThat(receive(PAID_PROBE, 10_000)).isInstanceOfSatisfying(OrderPaid.class, paid -> {
            assertThat(paid.orderId()).hasToString(id);
            assertThat(paid.userId()).isEqualTo(userId);
            assertThat(paid.eventId()).isEqualTo(eventId);
        });
        assertThat(receive(PAID_PROBE, 2_000)).isNull();
        assertThat(status(id)).isEqualTo("PAID");
        assertThat(quota(userId)).isEqualTo(2);
        assertThat(cancel(userId, id).code()).isEqualTo("ORDER_NOT_PENDING");
    }

    @Test
    void latePaymentOnExpiredOrderIsRefundedWithoutTickets() throws Exception {
        String id = post(userId, order(vip, 1)).body().path("id").asString();
        overdue(id);
        store.expireDue(Instant.now(), 200);

        paymentSucceeded(id);

        assertThat(receive(REFUND_PROBE, 10_000)).isInstanceOfSatisfying(OrderRefundRequested.class, refund -> {
            assertThat(refund.orderId()).hasToString(id);
            assertThat(refund.reason()).isEqualTo("ORDER_EXPIRED");
        });
        assertThat(receive(PAID_PROBE, 2_000)).isNull();
        assertThat(status(id)).isEqualTo("EXPIRED");
    }

    @Test
    void failedPaymentCancelsOrderAndReleasesTickets() throws Exception {
        String id = post(userId, order(vip, 2)).body().path("id").asString();

        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.PAYMENT_FAILED, new PaymentFailed(UUID.randomUUID(),
                Instant.now(), UUID.randomUUID(), UUID.fromString(id), "Thanh toán không thành công"));

        OrderCancelled cancelled = receiveCancelled();
        assertThat(cancelled).isNotNull();
        assertThat(cancelled.orderId()).hasToString(id);
        assertThat(cancelled.reason()).isEqualTo("PAYMENT_FAILED");
        assertThat(status(id)).isEqualTo("CANCELLED");
        assertThat(quota(userId)).isZero();
    }

    /** Người dùng hủy đúng lúc tiền về: đơn chỉ có một kết cục, và message khớp với kết cục đó. */
    @Test
    void paymentRacingCancelEndsInOneConsistentOutcome() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                UUID buyer = UUID.randomUUID();
                String id = post(buyer, order(vip, 1)).body().path("id").asString();
                PaymentSucceeded payment = new PaymentSucceeded(UUID.randomUUID(), Instant.now(), UUID.randomUUID(),
                        UUID.fromString(id), PRICE, "TX-" + round);

                Future<?> paid = pool.submit(() -> store.paymentSucceeded(payment));
                Future<Response> cancelled = pool.submit(() -> cancel(buyer, id));
                paid.get();
                int cancelStatus = cancelled.get().status();

                if (status(id).equals("PAID")) {
                    assertThat(cancelStatus).isEqualTo(409);
                    assertThat(receive(PAID_PROBE, 10_000)).isInstanceOf(OrderPaid.class);
                    assertThat(receive(REFUND_PROBE, 1_000)).isNull();
                } else {
                    assertThat(status(id)).isEqualTo("CANCELLED");
                    assertThat(cancelStatus).isEqualTo(200);
                    assertThat(receive(REFUND_PROBE, 10_000)).isInstanceOfSatisfying(OrderRefundRequested.class,
                            refund -> assertThat(refund.reason()).isEqualTo("ORDER_CANCELLED"));
                    assertThat(receive(PAID_PROBE, 1_000)).isNull();
                }
            }
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void cancelledEventCancelsItsOrdersAndRefundsPaidOnes() throws Exception {
        String waiting = post(userId, order(vip, 1)).body().path("id").asString();
        String paid = post(userId, order(standard, 1)).body().path("id").asString();
        String expired = post(userId, order(UUID.randomUUID(), 1)).body().path("id").asString();
        store.paymentSucceeded(new PaymentSucceeded(UUID.randomUUID(), Instant.now(), UUID.randomUUID(),
                UUID.fromString(paid), PRICE, "TX-" + paid));
        overdue(expired);
        store.expireDue(Instant.now(), 200);
        UUID otherEvent = UUID.randomUUID();
        doAnswer(call -> holdAll(call.getArgument(0), otherEvent)).when(tickets).reserve(any());
        String otherEventOrder = post(userId, Map.of("eventId", otherEvent,
                "items", List.of(Map.of("ticketTypeId", vip, "quantity", 1)))).body().path("id").asString();
        assertThat(quota(userId)).isEqualTo(2);

        EventCancelled cancelled = new EventCancelled(UUID.randomUUID(), Instant.now(), eventId);
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.EVENT_CANCELLED, cancelled);
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.EVENT_CANCELLED, cancelled);

        assertThat(receive(REFUND_PROBE, 10_000)).isInstanceOfSatisfying(OrderRefundRequested.class, refund -> {
            assertThat(refund.orderId()).hasToString(paid);
            assertThat(refund.reason()).isEqualTo("EVENT_CANCELLED");
        });
        assertThat(receive(REFUND_PROBE, 2_000)).isNull();
        assertThat(status(waiting)).isEqualTo("CANCELLED");
        assertThat(status(paid)).isEqualTo("CANCELLED");
        assertThat(status(expired)).isEqualTo("EXPIRED");
        assertThat(status(otherEventOrder)).isEqualTo("PENDING");
        assertThat(quota(userId)).isZero();
    }

    @Test
    void requiresLoginAndValidBody() throws Exception {
        assertThat(post(null, order(vip, 1)).status()).isEqualTo(401);
        assertThat(post(userId, order(vip, 0)).status()).isEqualTo(400);
        assertThat(post(userId, Map.of("items", List.of())).status()).isEqualTo(400);
    }
}
