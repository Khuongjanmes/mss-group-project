package com.openevent.event.events.dto;

import java.time.Instant;
import java.util.UUID;

import com.openevent.event.category.dto.CategoryResponse;
import com.openevent.event.events.Event;
import com.openevent.event.events.EventStatus;
import com.openevent.event.venue.dto.VenueResponse;

/** {@code organizationId}, {@code organizationName}: {@code null} với sự kiện cá nhân. */
public record EventResponse(UUID id, UUID organizerId, UUID organizationId, String organizationName, String title,
        String description, String bannerUrl, CategoryResponse category, VenueResponse venue, Instant startTime,
        Instant endTime, EventStatus status, String rejectReason, Instant createdAt) {

    public static EventResponse from(Event event) {
        return new EventResponse(event.getId(), event.getOrganizerId(), event.getOrganizationId(),
                event.getOrganization() == null ? null : event.getOrganization().getName(), event.getTitle(),
                event.getDescription(), event.getBannerUrl(), CategoryResponse.from(event.getCategory()),
                VenueResponse.from(event.getVenue()), event.getStartTime(), event.getEndTime(), event.getStatus(),
                event.getRejectReason(), event.getCreatedAt());
    }
}
