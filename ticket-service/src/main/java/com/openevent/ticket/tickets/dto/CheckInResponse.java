package com.openevent.ticket.tickets.dto;

import java.time.Instant;
import java.util.UUID;

import com.openevent.ticket.client.IdentityClient.UserContact;
import com.openevent.ticket.tickets.Ticket;
import com.openevent.ticket.tickettypes.TicketType;

/** Kết quả check-in cho người soát vé: họ tên, email người mua để đối chiếu; Identity lỗi thì để trống. */
public record CheckInResponse(UUID ticketId, String code, Ticket.Status status, Instant checkedInAt,
        String ticketTypeName, UUID eventId, String ownerName, String ownerEmail) {

    public static CheckInResponse from(Ticket ticket, TicketType type, UserContact owner) {
        return new CheckInResponse(ticket.getId(), ticket.getCode(), ticket.getStatus(), ticket.getCheckedInAt(),
                type.getName(), type.getEventId(), owner == null ? null : owner.fullName(),
                owner == null ? null : owner.email());
    }
}
