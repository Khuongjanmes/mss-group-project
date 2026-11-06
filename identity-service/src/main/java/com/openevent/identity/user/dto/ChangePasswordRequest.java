package com.openevent.identity.user.dto;

import com.openevent.identity.auth.dto.ValidPassword;

import jakarta.validation.constraints.NotNull;

public record ChangePasswordRequest(@NotNull String currentPassword, @ValidPassword String newPassword) {
}
