package com.openevent.ticket.tickets.dto;

import java.time.Instant;
import java.util.UUID;

import com.openevent.ticket.tickets.Ticket;
import com.openevent.ticket.tickettypes.TicketType;

/** Thời gian sự kiện lấy từ bản cache trong loại vé; tên sự kiện SPA lấy từ Event. */
public record TicketResponse(UUID id, String code, Ticket.Status status, Instant issuedAt, Instant checkedInAt,
        UUID orderId, UUID ownerId, UUID ticketTypeId, String ticketTypeName, UUID eventId, Instant eventStartTime,
        Instant eventEndTime) {

    public static TicketResponse from(Ticket ticket, TicketType type) {
        return new TicketResponse(ticket.getId(), ticket.getCode(), ticket.getStatus(), ticket.getIssuedAt(),
                ticket.getCheckedInAt(), ticket.getOrderId(), ticket.getOwnerId(), ticket.getTicketTypeId(),
                type == null ? null : type.getName(), type == null ? null : type.getEventId(),
                type == null ? null : type.getEventStartTime(), type == null ? null : type.getEventEndTime());
    }
}
