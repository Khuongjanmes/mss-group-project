package com.openevent.identity.auth.dto;

import com.openevent.identity.user.dto.UserResponse;

public record AuthResponse(String tokenType, String accessToken, long expiresIn, String refreshToken,
        UserResponse user) {
}
