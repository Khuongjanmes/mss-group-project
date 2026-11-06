package com.openevent.common.messaging.events;

import java.time.Instant;
import java.util.UUID;

/** Order phát; Ticket nhận và phát hành vé (KB-4). */
public record OrderPaid(UUID messageId, Instant occurredAt, UUID orderId, UUID userId, UUID eventId)
        implements DomainEvent {
}
