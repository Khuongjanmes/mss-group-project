package com.openevent.common.messaging.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ticket phát; Notification nhận và gửi email kèm QR (KB-4). */
public record TicketIssued(UUID messageId, Instant occurredAt, UUID orderId, UUID ownerId, UUID eventId,
        List<String> ticketCodes) implements DomainEvent {
}
