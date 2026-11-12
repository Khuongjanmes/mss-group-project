package com.openevent.event.organization.dto;

import java.util.UUID;

import com.openevent.event.organization.OrganizationRole;

/** {@code email}, {@code fullName} lấy từ Identity; {@code null} khi Identity không phản hồi. */
public record MemberResponse(UUID userId, String email, String fullName, OrganizationRole role,
        boolean canCreateEvents) {
}
