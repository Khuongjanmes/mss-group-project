package com.openevent.identity.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^\\P{Cntrl}*$", message = "không được chứa ký tự điều khiển")
        String fullName,
        @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "số điện thoại không hợp lệ")
        String phone,
        @Size(max = 500) @Pattern(regexp = "(?i)^https?://\\S+$", message = "phải là URL http hoặc https")
        String avatarUrl) {
}
