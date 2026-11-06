package com.openevent.order.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ticket-service")
public interface TicketClient {

    record ReservationItem(UUID ticketTypeId, int quantity) {
    }

    record ReservationRequest(UUID orderId, List<ReservationItem> items) {
    }

    record ReservedItem(UUID ticketTypeId, int quantity, BigDecimal unitPrice) {
    }

    record ReservationResponse(UUID orderId, List<ReservedItem> items) {
    }

    /** KB-1: giữ vé tất cả hoặc không. Hết vé trả 409; gọi lại cùng orderId không giữ vé hai lần. */
    @PostMapping("/internal/reservations")
    ReservationResponse reserve(@RequestBody ReservationRequest request);
}
