package com.openevent.ticket.tickettypes.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Organizer #7, phần của Ticket: vé đã bán và check-in. Doanh thu lấy từ Order. */
public record TicketTypeStats(UUID id, String name, BigDecimal price, int totalQuantity, int soldQuantity,
        int reservedQuantity, long checkedIn) {
}
