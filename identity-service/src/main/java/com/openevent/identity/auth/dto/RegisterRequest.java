package com.openevent.identity.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        // Chỉ ASCII: email được Gateway truyền xuống service qua HTTP header.
        @NotBlank @Email @Size(max = 254) @Pattern(regexp = "^\\p{ASCII}*$", message = "email chỉ gồm ký tự ASCII")
        String email,
        @ValidPassword String password,
        @NotBlank @Size(max = 100) @Pattern(regexp = "^\\P{Cntrl}*$", message = "không được chứa ký tự điều khiển")
        String fullName) {
}
