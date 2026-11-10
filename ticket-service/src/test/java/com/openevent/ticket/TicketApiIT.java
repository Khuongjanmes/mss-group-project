package com.openevent.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.openevent.common.messaging.Messaging;
import com.openevent.common.messaging.events.EventCancelled;
import com.openevent.common.messaging.events.OrderCancelled;
import com.openevent.common.messaging.events.OrderPaid;
import com.openevent.common.messaging.events.TicketIssued;
import com.openevent.common.messaging.events.TicketsCancelled;
import com.openevent.ticket.reservations.ReservationService;

import tools.jackson.databind.JsonNode;

class TicketApiIT extends TicketIntegrationTest {

    @Autowired
    ReservationService reservations;

    private void pay(UUID orderId, UUID eventId, Caller owner) {
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.ORDER_PAID,
                new OrderPaid(UUID.randomUUID(), Instant.now(), orderId, owner.id(), eventId));
    }

    private JsonNode myTickets(Caller caller) {
        return send("GET", "/api/tickets/my", null, caller).body();
    }

    @Test
    void onlyEventManagersChangeTicketTypes() {
        UUID eventId = event(Duration.ofDays(3));
        Instant saleEnd = Instant.now().plus(Duration.ofDays(2));

        assertThat(send("POST", "/api/ticket-types", typeBody(eventId, "VIP", 10, "500000", saleEnd), stranger).code())
                .isEqualTo("NOT_EVENT_MANAGER");
        assertThat(send("POST", "/api/ticket-types",
                typeBody(eventId, "VIP", 10, "500000", Instant.now().plus(Duration.ofDays(4))), host).code())
                .isEqualTo("INVALID_SALE_TIME");
        assertThat(send("POST", "/api/ticket-types", typeBody(UUID.randomUUID(), "VIP", 10, "1", saleEnd), host).code())
                .isEqualTo("EVENT_NOT_FOUND");

        String id = createType(eventId, "VIP", 10, "500000");
        assertThat(send("GET", "/api/ticket-types?eventId=" + eventId, null, null).body().get(0).path("available")
                .asInt()).isEqualTo(10);

        // Sửa: sự kiện lấy từ loại vé trong DB, bỏ qua eventId client gửi (KB-3 bước 4).
        fakeEvents.get(eventId).managers().add(stranger.id());
        Map<String, Object> changed = typeBody(UUID.randomUUID(), "VIP mới", 20, "450000", saleEnd);
        Response updated = send("PUT", "/api/ticket-types/" + id, changed, stranger);
        assertThat(updated.text("name")).isEqualTo("VIP mới");
        assertThat(updated.body().path("totalQuantity").asInt()).isEqualTo(20);
        assertThat(updated.text("eventId")).isEqualTo(eventId.toString());
        fakeEvents.get(eventId).managers().remove(stranger.id());
        assertThat(send("DELETE", "/api/ticket-types/" + id, null, stranger).code()).isEqualTo("NOT_EVENT_MANAGER");

        eventDown = true;
        try {
            assertThat(send("DELETE", "/api/ticket-types/" + id, null, host).code()).isEqualTo("EVENT_UNAVAILABLE");
        } finally {
            eventDown = false;
        }
        assertThat(send("DELETE", "/api/ticket-types/" + id, null, host).status()).isEqualTo(204);
    }

    @Test
    void reservationIsAllOrNothingAndRetrySafe() {
        UUID eventId = event(Duration.ofDays(3));
        String standard = createType(eventId, "Thường", 5, "100000");
        String vip = createType(eventId, "VIP", 2, "300000");
        UUID first = UUID.randomUUID();

        Response held = reserve(first, Map.of(standard, 2, vip, 1));
        assertThat(held.status()).isEqualTo(200);
        assertThat(held.text("eventId")).isEqualTo(eventId.toString());
        assertThat(held.body().path("items").valueStream().map(item -> item.path("unitPrice").decimalValue()))
                .usingElementComparator(java.math.BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new java.math.BigDecimal("100000"), new java.math.BigDecimal("300000"));

        // Gọi lại cùng orderId (KB-1 bước 6): không giữ thêm.
        assertThat(reserve(first, Map.of(standard, 2, vip, 1)).status()).isEqualTo(200);
        assertThat(type(standard).path("reservedQuantity").asInt()).isEqualTo(2);

        // VIP còn 1: cả đơn bị từ chối, loại Thường không bị giữ thêm.
        assertThat(reserve(UUID.randomUUID(), Map.of(standard, 1, vip, 2)).code()).isEqualTo("SOLD_OUT");
        assertThat(type(standard).path("available").asInt()).isEqualTo(3);

        // BR-06: không giảm tổng xuống dưới số đang giữ.
        Map<String, Object> shrink = typeBody(eventId, "Thường", 1, "100000",
                fakeEvents.get(eventId).startTime().minus(Duration.ofHours(1)));
        assertThat(send("PUT", "/api/ticket-types/" + standard, shrink, host).code()).isEqualTo("QUANTITY_BELOW_SOLD");
        assertThat(type(standard).path("totalQuantity").asInt()).isEqualTo(5);
        assertThat(type(standard).path("name").asString()).isEqualTo("Thường");
        assertThat(send("DELETE", "/api/ticket-types/" + vip, null, host).code()).isEqualTo("TICKET_TYPE_IN_USE");

        // Hết hạn: job nhả vé (BR-02).
        jdbc.update("update reservations set expires_at = now() - interval '1 hour' where order_id = ?", first);
        assertThat(reservations.releaseExpired(Instant.now())).isGreaterThanOrEqualTo(2);
        assertThat(type(standard).path("available").asInt()).isEqualTo(5);
        assertThat(type(vip).path("available").asInt()).isEqualTo(2);
    }

    @Test
    void saleWindowIsCheckedWhenReserving() {
        UUID eventId = event(Duration.ofDays(3));
        String id = createType(eventId, "Early bird", 5, "50000");
        jdbc.update("update ticket_types set sale_start = now() + interval '1 day' where id = ?::uuid", id);

        assertThat(reserve(UUID.randomUUID(), Map.of(id, 1)).code()).isEqualTo("SALE_NOT_OPEN");
    }

    /** Mục 3.5: 100 đơn cùng lúc giành 10 vé cuối, đúng 10 đơn giữ được vé (BR-01). */
    @Test
    void hundredConcurrentOrdersNeverOversell() throws Exception {
        UUID eventId = event(Duration.ofDays(3));
        String id = createType(eventId, "Vé cuối", 10, "200000");
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(100)) {
            for (int i = 0; i < 100; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return reserve(UUID.randomUUID(), Map.of(id, 1)).status();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(status -> status == 200).hasSize(10);
            assertThat(statuses).filteredOn(status -> status == 409).hasSize(90);
        }
        JsonNode type = type(id);
        assertThat(type.path("reservedQuantity").asInt()).isEqualTo(10);
        assertThat(type.path("available").asInt()).isZero();
    }

    @Test
    void cancelledOrderReturnsSeats() {
        UUID eventId = event(Duration.ofDays(3));
        String id = createType(eventId, "Thường", 3, "100000");
        UUID cancelled = UUID.randomUUID();
        reserve(cancelled, Map.of(id, 3));
        assertThat(type(id).path("available").asInt()).isZero();

        OrderCancelled message = new OrderCancelled(UUID.randomUUID(), Instant.now(), cancelled, "Hết hạn");
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.ORDER_CANCELLED, message);
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.ORDER_CANCELLED, message);
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(type(id).path("available").asInt()).isEqualTo(3));
        assertThat(type(id).path("reservedQuantity").asInt()).isZero();
    }

    @Test
    void paidOrderGetsTicketsOnce() {
        String issuedQueue = probe("ticket-issued-probe", Messaging.TICKET_ISSUED);
        UUID eventId = event(Duration.ofDays(3));
        String id = createType(eventId, "Thường", 5, "100000");
        UUID paid = UUID.randomUUID();
        reserve(paid, Map.of(id, 2));

        OrderPaid message = new OrderPaid(UUID.randomUUID(), Instant.now(), paid, buyer.id(), eventId);
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.ORDER_PAID, message);
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.ORDER_PAID, message);
        pay(paid, eventId, buyer);

        Object issued = rabbit.receiveAndConvert(issuedQueue, Duration.ofSeconds(15).toMillis());
        assertThat(issued).isInstanceOfSatisfying(TicketIssued.class, event -> {
            assertThat(event.ownerId()).isEqualTo(buyer.id());
            assertThat(event.ticketCodes()).hasSize(2);
        });
        assertThat(rabbit.receiveAndConvert(issuedQueue, 2000)).isNull();
        assertThat(myTickets(buyer)).hasSize(2);
        assertThat(myTickets(buyer).get(0).path("ticketTypeName").asString()).isEqualTo("Thường");
        assertThat(type(id).path("soldQuantity").asInt()).isEqualTo(2);
        assertThat(type(id).path("reservedQuantity").asInt()).isZero();

    }

    @Test
    void staffChecksInOnceDuringEventDaysAndHostKeepsWorkingWhenEventIsDown() {
        UUID eventId = event(Duration.ofMinutes(2));
        Instant saleEnd = Instant.now().plus(Duration.ofMinutes(1));
        Response created = send("POST", "/api/ticket-types", typeBody(eventId, "Vé cổng", 3, "0", saleEnd), host);
        String id = created.text("id");
        UUID orderId = UUID.randomUUID();
        reserve(orderId, Map.of(id, 3));
        pay(orderId, eventId, buyer);
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> myTickets(buyer).size() == 3);
        List<String> codes = myTickets(buyer).valueStream().map(ticket -> ticket.path("code").asString()).toList();

        Caller staff = stranger;
        assertThat(send("POST", "/api/tickets/check-in", Map.of("code", codes.get(0)), staff).code())
                .isEqualTo("NOT_EVENT_MANAGER");
        fakeEvents.get(eventId).staff().add(staff.id());
        Response checkedIn = send("POST", "/api/tickets/check-in", Map.of("code", codes.get(0)), staff);
        assertThat(checkedIn.text("status")).isEqualTo("CHECKED_IN");
        assertThat(send("POST", "/api/tickets/check-in", Map.of("code", codes.get(0)), staff).code())
                .isEqualTo("ALREADY_CHECKED_IN");
        assertThat(send("POST", "/api/tickets/check-in", Map.of("code", "khong-co"), staff).code())
                .isEqualTo("TICKET_NOT_FOUND");

        // Event lỗi: bản cache chỉ có HOST (KB-3 bước 3).
        eventDown = true;
        try {
            assertThat(send("POST", "/api/tickets/check-in", Map.of("code", codes.get(1)), staff).code())
                    .isEqualTo("EVENT_UNAVAILABLE");
            assertThat(send("POST", "/api/tickets/check-in", Map.of("code", codes.get(1)), host).text("status"))
                    .isEqualTo("CHECKED_IN");
        } finally {
            eventDown = false;
        }

    }

    @Test
    void checkInOutsideEventDaysIsRejected() {
        UUID eventId = event(Duration.ofDays(3));
        String id = createType(eventId, "Thường", 1, "0");
        UUID orderId = UUID.randomUUID();
        reserve(orderId, Map.of(id, 1));
        pay(orderId, eventId, buyer);
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> ticketOfType(id) != null);

        assertThat(send("POST", "/api/tickets/check-in", Map.of("code", ticketOfType(id)), host).code())
                .isEqualTo("OUTSIDE_EVENT_DAYS");
    }

    @Test
    void cancelledEventCancelsTicketsAndNotifiesOwners() {
        String cancelledQueue = probe("tickets-cancelled-probe", Messaging.TICKETS_CANCELLED);
        UUID eventId = event(Duration.ofDays(3));
        String id = createType(eventId, "Thường", 5, "100000");
        UUID paid = UUID.randomUUID();
        reserve(paid, Map.of(id, 1));
        pay(paid, eventId, buyer);
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> ticketOfType(id) != null);
        reserve(UUID.randomUUID(), Map.of(id, 2));

        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.EVENT_CANCELLED,
                new EventCancelled(UUID.randomUUID(), Instant.now(), eventId));

        assertThat(rabbit.receiveAndConvert(cancelledQueue, Duration.ofSeconds(15).toMillis()))
                .isInstanceOfSatisfying(TicketsCancelled.class, event -> assertThat(event.ownerIds()).containsExactly(buyer.id()));
        assertThat(myTickets(buyer).valueStream().map(ticket -> ticket.path("status").asString()))
                .containsExactly("CANCELLED");
        assertThat(send("POST", "/api/tickets/check-in", Map.of("code", ticketOfType(id)), host).code())
                .isEqualTo("TICKET_CANCELLED");
        assertThat(type(id).path("reservedQuantity").asInt()).isZero();
    }

    private String ticketOfType(String typeId) {
        return myTickets(buyer).valueStream().filter(ticket -> ticket.path("ticketTypeId").asString().equals(typeId))
                .map(ticket -> ticket.path("code").asString()).findFirst().orElse(null);
    }
}
