package com.openevent.event.organization;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.openevent.common.web.ApiException;
import com.openevent.event.client.IdentityClient.UserContact;
import com.openevent.event.client.UserDirectory;
import com.openevent.event.events.EventRepository;
import com.openevent.event.events.EventStatus;
import com.openevent.event.organization.dto.MemberRequest;
import com.openevent.event.organization.dto.MemberResponse;
import com.openevent.event.organization.dto.MemberUpdateRequest;
import com.openevent.event.organization.dto.OrganizationRequest;
import com.openevent.event.organization.dto.OrganizationResponse;

import lombok.RequiredArgsConstructor;

/**
 * Quyền theo BR-14: OWNER làm mọi việc; MANAGER mời và xóa MEMBER; MEMBER chỉ tự rời. Chỉ OWNER đổi vai trò, cấp
 * quyền tạo sự kiện cho MEMBER và chuyển quyền sở hữu.
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    /** Sự kiện chưa kết thúc hoặc chưa hủy: HOST của chúng không được rời tổ chức (BR-14). */
    private static final List<EventStatus> ACTIVE = List.of(EventStatus.DRAFT, EventStatus.PENDING_APPROVAL,
            EventStatus.REJECTED, EventStatus.PUBLISHED);

    private final OrganizationRepository organizations;
    private final EventRepository events;
    private final UserDirectory users;
    /** Cho các thao tác gọi Identity: không giữ kết nối DB trong lúc chờ mạng. */
    private final TransactionTemplate tx;

    @Transactional(readOnly = true)
    public List<OrganizationResponse> mine(UUID callerId) {
        return organizations.findAllByMember(callerId).stream()
                .map(organization -> OrganizationResponse.from(organization, callerId)).toList();
    }

    @Transactional
    public OrganizationResponse create(UUID callerId, OrganizationRequest request) {
        Organization organization = Organization.create(request.name().trim(), request.description(), callerId);
        return OrganizationResponse.from(organizations.save(organization), callerId);
    }

    @Transactional(readOnly = true)
    public OrganizationResponse get(UUID callerId, UUID id) {
        return OrganizationResponse.from(requireMember(callerId, id), callerId);
    }

    @Transactional
    public OrganizationResponse update(UUID callerId, UUID id, OrganizationRequest request) {
        Organization organization = requireOwner(callerId, id);
        organization.update(request.name().trim(), request.description());
        return OrganizationResponse.from(organization, callerId);
    }

    /** OWNER đứng đầu, rồi MANAGER, MEMBER; họ tên và email lấy từ Identity. */
    public List<MemberResponse> members(UUID callerId, UUID id) {
        Map<UUID, Membership> members = tx.execute(status -> Map.copyOf(requireMember(callerId, id).members()));
        Map<UUID, UserContact> contacts = users.contacts(members.keySet());
        return members.entrySet().stream()
                .map(entry -> toResponse(entry.getKey(), entry.getValue(), contacts.get(entry.getKey())))
                .sorted(Comparator.comparing(MemberResponse::role)
                        .thenComparing(member -> member.fullName() == null ? "" : member.fullName()))
                .toList();
    }

    /** OWNER mời MANAGER hoặc MEMBER; MANAGER chỉ mời MEMBER. Kiểm tra quyền lại sau khi hỏi Identity. */
    public MemberResponse addMember(UUID callerId, UUID id, MemberRequest request) {
        tx.executeWithoutResult(status -> requireInviter(callerId, id, request.role()));
        UserContact user = users.findAssignable(request.email());
        return tx.execute(status -> {
            Organization organization = requireInviter(callerId, id, request.role());
            organization.addMember(user.id(), request.role());
            return toResponse(user.id(), organization.requireMember(user.id()), user);
        });
    }

    public MemberResponse changeMember(UUID callerId, UUID id, UUID userId, MemberUpdateRequest request) {
        Membership membership = tx.execute(status -> {
            Organization organization = requireOwner(callerId, id);
            organization.changeMember(userId, request.role(), request.canCreateEvents());
            return organization.requireMember(userId);
        });
        return toResponse(userId, membership, users.contacts(List.of(userId)).get(userId));
    }

    /** Ai cũng tự rời được (trừ OWNER); OWNER xóa ai cũng được; MANAGER chỉ xóa MEMBER. */
    @Transactional
    public void removeMember(UUID callerId, UUID id, UUID userId) {
        Organization organization = requireMember(callerId, id);
        if (!callerId.equals(userId)) {
            OrganizationRole callerRole = organization.requireMember(callerId).getRole();
            OrganizationRole targetRole = organization.requireMember(userId).getRole();
            boolean allowed = callerRole == OrganizationRole.OWNER
                    || callerRole == OrganizationRole.MANAGER && targetRole == OrganizationRole.MEMBER;
            if (!allowed) {
                throw forbidden("Bạn không có quyền xóa thành viên này");
            }
        }
        if (events.existsByOrganization_IdAndOrganizerIdAndStatusIn(id, userId, ACTIVE)) {
            throw new ApiException(HttpStatus.CONFLICT, "MEMBER_HOSTS_EVENTS",
                    "Người này đang làm HOST sự kiện của tổ chức; đổi HOST trước khi xóa");
        }
        organization.removeMember(userId);
    }

    @Transactional
    public OrganizationResponse transferOwnership(UUID callerId, UUID id, UUID newOwnerId) {
        Organization organization = requireOwner(callerId, id);
        organization.stepDownOwner(callerId, newOwnerId);
        organizations.flush();
        organization.promoteToOwner(newOwnerId);
        return OrganizationResponse.from(organization, callerId);
    }

    private Organization requireInviter(UUID callerId, UUID id, OrganizationRole invitedRole) {
        Organization organization = requireMember(callerId, id);
        OrganizationRole callerRole = organization.requireMember(callerId).getRole();
        boolean allowed = callerRole == OrganizationRole.OWNER
                || callerRole == OrganizationRole.MANAGER && invitedRole == OrganizationRole.MEMBER;
        if (!allowed) {
            throw forbidden(callerRole == OrganizationRole.MANAGER
                    ? "MANAGER chỉ mời được MEMBER" : "Chỉ OWNER hoặc MANAGER mời được thành viên");
        }
        return organization;
    }

    /** Người ngoài tổ chức nhận 404 để không lộ tổ chức tồn tại. */
    private Organization requireMember(UUID callerId, UUID id) {
        return organizations.findById(id).filter(organization -> organization.membershipOf(callerId).isPresent())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND",
                        "Không tìm thấy tổ chức"));
    }

    private Organization requireOwner(UUID callerId, UUID id) {
        Organization organization = requireMember(callerId, id);
        if (organization.requireMember(callerId).getRole() != OrganizationRole.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_ORGANIZATION_OWNER", "Chỉ OWNER của tổ chức làm được");
        }
        return organization;
    }

    private static MemberResponse toResponse(UUID userId, Membership membership, UserContact contact) {
        return new MemberResponse(userId, contact == null ? null : contact.email(),
                contact == null ? null : contact.fullName(), membership.getRole(), membership.mayCreateEvents());
    }

    private static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "ORGANIZATION_PERMISSION_DENIED", message);
    }
}
