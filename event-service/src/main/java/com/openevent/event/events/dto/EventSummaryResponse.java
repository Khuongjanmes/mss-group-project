package com.openevent.event.events.dto;

import java.time.Instant;
import java.util.UUID;

import com.openevent.event.events.Event;
import com.openevent.event.events.EventStatus;
import com.openevent.event.venue.Venue;

/**
 * Cho Feign của Order (KB-1), Ticket (KB-3) và Notification (email vé, KB-4); giữ khớp với
 * {@code EventClient.EventSummary} ở các service đó. Thêm trường mới không làm hỏng client cũ.
 */
public record EventSummaryResponse(UUID id, UUID organizerId, String title, EventStatus status, Instant startTime,
        Instant endTime, String venueName, String venueAddress, String venueCity) {

    public static EventSummaryResponse from(Event event) {
        Venue venue = event.getVenue();
        return new EventSummaryResponse(event.getId(), event.getOrganizerId(), event.getTitle(), event.getStatus(),
                event.getStartTime(), event.getEndTime(), venue.getName(), venue.getAddress(), venue.getCity());
    }
}
