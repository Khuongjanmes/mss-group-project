package com.openevent.identity.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code idToken} là credential mà SPA nhận từ Google Identity Services. */
public record GoogleLoginRequest(@NotBlank String idToken) {
}
