package com.openevent.common.messaging.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Payment phát; Order nhận (KB-4). */
public record PaymentSucceeded(UUID messageId, Instant occurredAt, UUID paymentId, UUID orderId, BigDecimal amount,
        String transactionRef) implements DomainEvent {
}
