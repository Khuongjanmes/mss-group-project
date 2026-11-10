package com.openevent.ticket.client;

import java.time.Instant;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "event-service")
public interface EventClient {

    record EventSummary(UUID id, UUID organizerId, String status, Instant startTime, Instant endTime) {
    }

    /** {@code canManage}: HOST, OWNER/MANAGER; {@code canCheckIn}: thêm STAFF. */
    record EventAccess(boolean canManage, boolean canCheckIn) {
    }

    /** KB-3: xác minh organizer và ngày diễn ra (BR-09). */
    @GetMapping("/internal/events/{id}")
    EventSummary getEvent(@PathVariable("id") UUID id);

    /** KB-3: Ticket không tự suy ra vai trò tổ chức mà hỏi Event. */
    @GetMapping("/internal/events/{id}/access")
    EventAccess getAccess(@PathVariable("id") UUID id, @RequestParam("userId") UUID userId);
}
