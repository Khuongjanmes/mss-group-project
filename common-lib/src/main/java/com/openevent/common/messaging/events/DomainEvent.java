package com.openevent.common.messaging.events;

import java.time.Instant;
import java.util.UUID;

/** Sự kiện qua RabbitMQ. Consumer dùng {@link #messageId()} để bỏ qua message trùng (BR-11). */
public interface DomainEvent {

    UUID messageId();

    Instant occurredAt();
}
