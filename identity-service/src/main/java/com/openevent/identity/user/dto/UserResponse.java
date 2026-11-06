package com.openevent.identity.user.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.openevent.identity.user.AuthProvider;
import com.openevent.identity.user.User;
import com.openevent.identity.user.UserStatus;

public record UserResponse(UUID id, String email, String fullName, String phone, String avatarUrl,
        AuthProvider provider, UserStatus status, List<String> roles, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getAvatarUrl(), user.getProvider(), user.getStatus(), user.roleNames(), user.getCreatedAt());
    }
}
