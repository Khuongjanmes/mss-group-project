package com.openevent.common.messaging.events;

import java.time.Instant;
import java.util.UUID;

/** Order phát khi cần hoàn tiền; Payment nhận (BR-10, BR-13). */
public record OrderRefundRequested(UUID messageId, Instant occurredAt, UUID orderId, String reason)
        implements DomainEvent {
}
