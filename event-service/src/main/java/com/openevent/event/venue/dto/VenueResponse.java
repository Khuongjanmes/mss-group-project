package com.openevent.event.venue.dto;

import java.util.UUID;

import com.openevent.event.venue.Venue;

public record VenueResponse(UUID id, String name, String address, String city, Integer capacity) {

    public static VenueResponse from(Venue venue) {
        return new VenueResponse(venue.getId(), venue.getName(), venue.getAddress(), venue.getCity(),
                venue.getCapacity());
    }
}
