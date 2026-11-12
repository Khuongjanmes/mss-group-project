package com.openevent.notification.client;

import java.time.Instant;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "event-service")
public interface EventClient {

    record EventSummary(UUID id, String title, Instant startTime, Instant endTime, String venueName,
            String venueAddress, String venueCity) {
    }

    /** KB-4: tên, thời gian, địa điểm sự kiện để ghi vào email vé. */
    @GetMapping("/internal/events/{id}")
    EventSummary getEvent(@PathVariable("id") UUID id);
}
