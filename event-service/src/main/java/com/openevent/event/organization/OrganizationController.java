package com.openevent.event.organization;

import java.util.List;
import java.util.UUID;

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
import com.openevent.event.events.EventService;
import com.openevent.event.events.EventStatus;
import com.openevent.event.events.dto.EventResponse;
import com.openevent.event.organization.dto.MemberRequest;
import com.openevent.event.organization.dto.MemberResponse;
import com.openevent.event.organization.dto.MemberUpdateRequest;
import com.openevent.event.organization.dto.OrganizationRequest;
import com.openevent.event.organization.dto.OrganizationResponse;
import com.openevent.event.organization.dto.UserIdRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/** Mọi tài khoản trừ Admin (Admin không tổ chức sự kiện); quyền theo tổ chức do service kiểm tra. */
@RestController
@PreAuthorize("isAuthenticated() and !hasRole('ADMIN')")
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;
    private final EventService eventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody OrganizationRequest request) {
        return organizationService.create(caller.id(), request);
    }

    @GetMapping("/my")
    public List<OrganizationResponse> mine(@AuthenticationPrincipal AuthenticatedUser caller) {
        return organizationService.mine(caller.id());
    }

    @GetMapping("/{id}")
    public OrganizationResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return organizationService.get(caller.id(), id);
    }

    @PutMapping("/{id}")
    public OrganizationResponse update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody OrganizationRequest request) {
        return organizationService.update(caller.id(), id, request);
    }

    @GetMapping("/{id}/members")
    public List<MemberResponse> members(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return organizationService.members(caller.id(), id);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse addMember(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody MemberRequest request) {
        return organizationService.addMember(caller.id(), id, request);
    }

    @PutMapping("/{id}/members/{userId}")
    public MemberResponse changeMember(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @PathVariable UUID userId, @Valid @RequestBody MemberUpdateRequest request) {
        return organizationService.changeMember(caller.id(), id, userId, request);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @PathVariable UUID userId) {
        organizationService.removeMember(caller.id(), id, userId);
    }

    @PutMapping("/{id}/owner")
    public OrganizationResponse transferOwnership(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody UserIdRequest request) {
        return organizationService.transferOwnership(caller.id(), id, request.userId());
    }

    @GetMapping("/{id}/events")
    public PageResponse<EventResponse> events(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return eventService.searchOrganization(caller.id(), id, status, page, size);
    }
}
