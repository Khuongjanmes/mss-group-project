package com.openevent.common.messaging.events;

import java.time.Instant;
import java.util.UUID;

/** Event phát; Ticket và Order nhận (KB-5). */
public record EventCancelled(UUID messageId, Instant occurredAt, UUID eventId) implements DomainEvent {
}
