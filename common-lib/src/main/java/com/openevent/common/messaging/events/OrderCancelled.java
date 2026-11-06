package com.openevent.common.messaging.events;

import java.time.Instant;
import java.util.UUID;

/** Order phát khi đơn bị hủy hoặc hết hạn; Ticket nhận và nhả vé (KB-4). */
public record OrderCancelled(UUID messageId, Instant occurredAt, UUID orderId, String reason)
        implements DomainEvent {
}
