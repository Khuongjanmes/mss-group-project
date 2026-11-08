package com.openevent.identity.user;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.openevent.common.security.AuthenticatedUser;
import com.openevent.common.web.PageResponse;
import com.openevent.identity.user.dto.ChangePasswordRequest;
import com.openevent.identity.user.dto.UpdateProfileRequest;
import com.openevent.identity.user.dto.UserResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser caller) {
        return userService.getCurrent(caller.id());
    }

    @PutMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(caller.id(), request);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(caller.id(), request.currentPassword(), request.newPassword());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserResponse> search(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(required = false) @Size(max = 100) @Pattern(regexp = "^\\P{Cntrl}*$") String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return userService.search(caller.id(), keyword, status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse getById(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return userService.getById(caller.id(), id);
    }

    @PutMapping("/{id}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse lock(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return userService.lock(caller.id(), id);
    }

    @PutMapping("/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse unlock(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return userService.unlock(caller.id(), id);
    }
}
