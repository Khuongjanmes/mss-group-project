package com.openevent.ticket.tickettypes.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.openevent.ticket.tickettypes.TicketType;

/** Công khai: không có bản cache HOST; {@code available} là số vé còn có thể giữ. */
public record TicketTypeResponse(UUID id, UUID eventId, String name, BigDecimal price, int totalQuantity,
        int soldQuantity, int reservedQuantity, int available, Instant saleStart, Instant saleEnd) {

    public static TicketTypeResponse from(TicketType type) {
        return new TicketTypeResponse(type.getId(), type.getEventId(), type.getName(), type.getPrice(),
                type.getTotalQuantity(), type.getSoldQuantity(), type.getReservedQuantity(), type.available(),
                type.getSaleStart(), type.getSaleEnd());
    }
}
