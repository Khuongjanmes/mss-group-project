package com.openevent.event.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.openevent.common.security.AuthenticatedUser;
import com.openevent.common.web.PageResponse;
import com.openevent.event.events.dto.EventRequest;
import com.openevent.event.events.dto.EventResponse;
import com.openevent.event.events.dto.RejectRequest;
import com.openevent.event.events.dto.StaffRequest;
import com.openevent.event.events.dto.StaffResponse;
import com.openevent.event.media.ImageStorage.UploadedImage;
import com.openevent.event.organization.dto.UserIdRequest;

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
            @RequestParam(required = false) EventTimeline timeline,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return eventService.searchPublished(new EventSearch(keyword, category, city, from, to, null, null, null),
                timeline, page, size);
    }

    @GetMapping("/check-in")
    @PreAuthorize("isAuthenticated() and !hasRole('ADMIN')")
    public List<EventResponse> checkInable(@AuthenticationPrincipal AuthenticatedUser caller) {
        return eventService.checkInable(caller.id());
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated() and !hasRole('ADMIN')")
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
        return eventService.searchForAdmin(new EventSearch(keyword, null, null, null, null, status, null, null), page, size);
    }

    @GetMapping("/{id}")
    public EventResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return eventService.get(id, caller);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated() and !hasRole('ADMIN')")
    public EventResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody EventRequest request) {
        return eventService.create(caller.id(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated() and !hasRole('ADMIN')")
    public EventResponse update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody EventRequest request) {
        return eventService.update(caller.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated() and !hasRole('ADMIN')")
    public void delete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        eventService.delete(caller.id(), id);
    }

    @PutMapping("/{id}/submit")
    @PreAuthorize("isAuthenticated() and !hasRole('ADMIN')")
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
    @PreAuthorize("isAuthenticated()")
    public EventResponse cancel(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return eventService.cancel(caller, id);
    }

    /** Ảnh bìa: multipart field {@code file}, JPG/PNG/WEBP tối đa 5 MB; trả về {@code url} để đưa vào bannerUrl. */
    @PostMapping(path = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated() and !hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadedImage uploadImage(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @RequestPart("file") MultipartFile file) {
        return eventService.uploadImage(caller.id(), id, file);
    }

    /** Đổi HOST sự kiện thuộc tổ chức (BR-14). */
    @PutMapping("/{id}/host")
    @PreAuthorize("isAuthenticated() and !hasRole('ADMIN')")
    public EventResponse assignHost(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody UserIdRequest request) {
        return eventService.assignHost(caller.id(), id, request.userId());
    }

    @GetMapping("/{id}/staff")
    @PreAuthorize("isAuthenticated() and !hasRole('ADMIN')")
    public List<StaffResponse> staff(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return eventService.staff(caller.id(), id);
    }

    @PostMapping("/{id}/staff")
    @PreAuthorize("isAuthenticated() and !hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffResponse addStaff(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody StaffRequest request) {
        return eventService.addStaff(caller.id(), id, request.email());
    }

    @DeleteMapping("/{id}/staff/{userId}")
    @PreAuthorize("isAuthenticated() and !hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeStaff(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @PathVariable UUID userId) {
        eventService.removeStaff(caller.id(), id, userId);
    }
}
