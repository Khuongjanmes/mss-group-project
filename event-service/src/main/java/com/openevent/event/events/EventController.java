package com.openevent.event.events;

import java.time.Instant;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.openevent.common.security.AuthenticatedUser;
import com.openevent.common.web.PageResponse;
import com.openevent.event.events.dto.EventRequest;
import com.openevent.event.events.dto.EventResponse;
import com.openevent.event.events.dto.RejectRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /** Công khai: tìm sự kiện PUBLISHED theo tên, slug danh mục, thành phố, khoảng thời gian. */
    @GetMapping
    public PageResponse<EventResponse> search(
            @RequestParam(required = false) @Size(max = 100) @Pattern(regexp = "^\\P{Cntrl}*$") String keyword,
            @RequestParam(required = false) @Size(max = 120) String category,
            @RequestParam(required = false) @Size(max = 100) String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return eventService.searchPublished(new EventSearch(keyword, category, city, from, to, null, null), page, size);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('ORGANIZER')")
    public PageResponse<EventResponse> mine(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return eventService.searchMine(caller.id(), status, page, size);
    }

    /** Admin xem mọi trạng thái, ví dụ {@code status=PENDING_APPROVAL} để duyệt. */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<EventResponse> searchForAdmin(
            @RequestParam(required = false) @Size(max = 100) @Pattern(regexp = "^\\P{Cntrl}*$") String keyword,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return eventService.searchForAdmin(new EventSearch(keyword, null, null, null, null, status, null), page, size);
    }

    @GetMapping("/{id}")
    public EventResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return eventService.get(id, caller);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ORGANIZER')")
    public EventResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody EventRequest request) {
        return eventService.create(caller.id(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public EventResponse update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody EventRequest request) {
        return eventService.update(caller.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ORGANIZER')")
    public void delete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        eventService.delete(caller.id(), id);
    }

    @PutMapping("/{id}/submit")
    @PreAuthorize("hasRole('ORGANIZER')")
    public EventResponse submit(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return eventService.submit(caller.id(), id);
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public EventResponse approve(@PathVariable UUID id) {
        return eventService.approve(id);
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public EventResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        return eventService.reject(id, request.reason());
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public EventResponse cancel(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return eventService.cancel(caller, id);
    }
}
