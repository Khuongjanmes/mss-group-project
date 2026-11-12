package com.openevent.event.organization.dto;

import java.time.Instant;
import java.util.UUID;

import com.openevent.event.organization.Membership;
import com.openevent.event.organization.Organization;
import com.openevent.event.organization.OrganizationRole;

/** {@code myRole}, {@code canCreateEvents}: vai trò và quyền tạo sự kiện của người gọi. */
public record OrganizationResponse(UUID id, String name, String description, OrganizationRole myRole,
        boolean canCreateEvents, int memberCount, Instant createdAt) {

    public static OrganizationResponse from(Organization organization, UUID callerId) {
        Membership mine = organization.membershipOf(callerId).orElse(null);
        return new OrganizationResponse(organization.getId(), organization.getName(), organization.getDescription(),
                mine == null ? null : mine.getRole(), mine != null && mine.mayCreateEvents(),
                organization.members().size(), organization.getCreatedAt());
    }
}
