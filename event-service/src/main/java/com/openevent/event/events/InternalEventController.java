package com.openevent.event.events;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.openevent.event.events.dto.EventAccessResponse;
import com.openevent.event.events.dto.EventSummaryResponse;

import lombok.RequiredArgsConstructor;

/** API cho các service khác gọi qua Feign. Gateway chặn /internal/** từ bên ngoài. */
@RestController
@RequestMapping("/internal/events")
@RequiredArgsConstructor
public class InternalEventController {

    private final EventService eventService;

    @GetMapping("/{id}")
    public EventSummaryResponse getById(@PathVariable UUID id) {
        return eventService.summary(id);
    }

    @GetMapping("/{id}/access")
    public EventAccessResponse access(@PathVariable UUID id, @RequestParam UUID userId) {
        return eventService.access(id, userId);
    }
}
