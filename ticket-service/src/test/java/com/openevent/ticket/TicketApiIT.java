package com.openevent.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TicketApiIT extends TicketIntegrationTest {

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

}
