package com.openevent.event.organization.dto;

import com.openevent.event.organization.OrganizationRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Mời thành viên bằng email tài khoản OpenEvent; {@code role} là MANAGER hoặc MEMBER. */
public record MemberRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull OrganizationRole role) {
}
