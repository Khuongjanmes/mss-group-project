package com.openevent.common.messaging.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ticket phát sau khi hủy vé của sự kiện; Notification gửi email (KB-5). */
public record TicketsCancelled(UUID messageId, Instant occurredAt, UUID eventId, List<UUID> ownerIds)
        implements DomainEvent {
}
