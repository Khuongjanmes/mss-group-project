package com.openevent.ticket.client;

import java.time.Instant;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "event-service")
public interface EventClient {

    record EventSummary(UUID id, UUID organizerId, String status, Instant startTime, Instant endTime) {
    }

    /** KB-3: xác minh organizer và ngày diễn ra (BR-09). */
    @GetMapping("/internal/events/{id}")
    EventSummary getEvent(@PathVariable("id") UUID id);
}
