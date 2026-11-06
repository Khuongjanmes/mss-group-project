package com.openevent.common.messaging.events;

import java.time.Instant;
import java.util.UUID;

/** Payment phát; Order nhận (KB-4). */
public record PaymentFailed(UUID messageId, Instant occurredAt, UUID paymentId, UUID orderId, String reason)
        implements DomainEvent {
}
