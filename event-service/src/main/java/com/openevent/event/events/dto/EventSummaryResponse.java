package com.openevent.event.events.dto;

import java.time.Instant;
import java.util.UUID;

import com.openevent.event.events.Event;
import com.openevent.event.events.EventStatus;

/** Cho Feign của Order (KB-1) và Ticket (KB-3); giữ khớp với {@code EventClient.EventSummary} ở hai service đó. */
public record EventSummaryResponse(UUID id, UUID organizerId, String title, EventStatus status, Instant startTime,
        Instant endTime) {

    public static EventSummaryResponse from(Event event) {
        return new EventSummaryResponse(event.getId(), event.getOrganizerId(), event.getTitle(), event.getStatus(),
                event.getStartTime(), event.getEndTime());
    }
}
