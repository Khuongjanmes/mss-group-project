package com.openevent.event.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một dòng của organization_members; bất biến, đổi thì thay bằng bản mới. */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Membership {

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private OrganizationRole role;

    /** Chỉ có nghĩa với MEMBER; OWNER và MANAGER luôn tạo được sự kiện. */
    @Column(name = "can_create_events", nullable = false)
    private boolean canCreateEvents;

    public Membership(OrganizationRole role, boolean canCreateEvents) {
        this.role = role;
        this.canCreateEvents = role == OrganizationRole.MEMBER && canCreateEvents;
    }

    public boolean mayCreateEvents() {
        return role.managesAllEvents() || canCreateEvents;
    }
}
