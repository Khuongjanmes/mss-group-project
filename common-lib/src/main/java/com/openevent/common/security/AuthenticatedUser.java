package com.openevent.common.security;

import java.util.Set;
import java.util.UUID;

/** Người gọi hiện tại. Lấy trong controller bằng {@code @AuthenticationPrincipal AuthenticatedUser user}. */
public record AuthenticatedUser(UUID id, String email, Set<String> roles) {

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
