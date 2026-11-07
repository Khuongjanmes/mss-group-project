package com.openevent.event.organization.dto;

import com.openevent.event.organization.OrganizationRole;

import jakarta.validation.constraints.NotNull;

/** OWNER đổi vai trò (MANAGER/MEMBER) và quyền tạo sự kiện của MEMBER. */
public record MemberUpdateRequest(@NotNull OrganizationRole role, boolean canCreateEvents) {
}
