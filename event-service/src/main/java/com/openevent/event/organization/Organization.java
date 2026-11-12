package com.openevent.event.organization;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.openevent.common.web.ApiException;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Tổ chức và thành viên (BR-14). Ai được làm gì do {@link OrganizationService} kiểm tra. */
@Entity
@Table(name = "organizations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ElementCollection
    @CollectionTable(name = "organization_members", joinColumns = @JoinColumn(name = "organization_id"))
    @MapKeyColumn(name = "user_id")
    @Getter(AccessLevel.NONE)
    private Map<UUID, Membership> members = new HashMap<>();

    public static Organization create(String name, String description, UUID ownerId) {
        Organization organization = new Organization();
        organization.update(name, description);
        organization.members.put(ownerId, new Membership(OrganizationRole.OWNER, false));
        return organization;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Map<UUID, Membership> members() {
        return Collections.unmodifiableMap(members);
    }

    public Optional<Membership> membershipOf(UUID userId) {
        return Optional.ofNullable(members.get(userId));
    }

    public void addMember(UUID userId, OrganizationRole role) {
        requireNotOwnerRole(role);
        if (members.containsKey(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", "Người này đã ở trong tổ chức");
        }
        members.put(userId, new Membership(role, false));
    }

    public void changeMember(UUID userId, OrganizationRole role, boolean canCreateEvents) {
        requireNotOwnerRole(role);
        requireNotOwner(userId);
        members.put(userId, new Membership(role, canCreateEvents));
    }

    public void removeMember(UUID userId) {
        requireNotOwner(userId);
        members.remove(userId);
    }

    /** Bước 1 của chuyển quyền: OWNER cũ xuống MANAGER. Phải flush trước bước 2 vì DB chỉ cho 1 OWNER. */
    public void stepDownOwner(UUID ownerId, UUID newOwnerId) {
        if (requireMember(newOwnerId).getRole() == OrganizationRole.OWNER) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_OWNER", "Người này đã là OWNER");
        }
        members.put(ownerId, new Membership(OrganizationRole.MANAGER, false));
    }

    public void promoteToOwner(UUID newOwnerId) {
        members.put(newOwnerId, new Membership(OrganizationRole.OWNER, false));
    }

    public Membership requireMember(UUID userId) {
        Membership membership = members.get(userId);
        if (membership == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Người này không ở trong tổ chức");
        }
        return membership;
    }

    private void requireNotOwner(UUID userId) {
        if (requireMember(userId).getRole() == OrganizationRole.OWNER) {
            throw new ApiException(HttpStatus.CONFLICT, "OWNER_CANNOT_LEAVE",
                    "OWNER phải chuyển quyền sở hữu trước khi rời tổ chức hoặc đổi vai trò");
        }
    }

    private static void requireNotOwnerRole(OrganizationRole role) {
        if (role == OrganizationRole.OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE",
                    "Dùng chuyển quyền sở hữu để đổi OWNER");
        }
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
