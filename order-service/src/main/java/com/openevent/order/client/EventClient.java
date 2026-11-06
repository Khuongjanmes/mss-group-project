package com.openevent.order.client;

import java.time.Instant;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "event-service")
public interface EventClient {

    record EventSummary(UUID id, UUID organizerId, String status, Instant startTime, Instant endTime) {
    }

    /** KB-1: kiểm tra sự kiện đang PUBLISHED (BR-04). */
    @GetMapping("/internal/events/{id}")
    EventSummary getEvent(@PathVariable("id") UUID id);
}
