package com.openevent.ticket.reservations.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.openevent.ticket.reservations.Reservation;

/** Khớp {@code TicketClient.ReservationResponse} của Order: Order tính tổng tiền theo {@code unitPrice} (KB-1). */
public record ReservationResponse(UUID orderId, UUID eventId, Instant expiresAt, List<Item> items) {

    public record Item(UUID ticketTypeId, int quantity, BigDecimal unitPrice) {

        public static Item from(Reservation reservation) {
            return new Item(reservation.getTicketTypeId(), reservation.getQuantity(), reservation.getUnitPrice());
        }
    }
}
