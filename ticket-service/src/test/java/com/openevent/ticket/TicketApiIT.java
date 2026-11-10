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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.openevent.ticket.reservations.ReservationService;

import tools.jackson.databind.JsonNode;

class TicketApiIT extends TicketIntegrationTest {

    @Autowired
    ReservationService reservations;

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

}
