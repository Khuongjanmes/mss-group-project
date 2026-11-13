package com.openevent.event.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.openevent.common.web.ApiException;
import com.openevent.event.client.IdentityClient.UserContact;
import com.openevent.event.client.UserDirectory;
import com.openevent.event.events.EventRepository;
import com.openevent.event.organization.dto.MemberRequest;
import com.openevent.event.organization.dto.MemberUpdateRequest;

/** BR-14: OWNER, MANAGER, MEMBER và quyền mời, xóa, chuyển quyền sở hữu trong tổ chức. */
class OrganizationServiceTest {

    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final UserDirectory users = mock(UserDirectory.class);
    private final OrganizationService service = new OrganizationService(organizations, events, users,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

    private final UUID ownerId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private Organization organization;

    @BeforeEach
    void organization() {
        organization = Organization.create("CLB Lập trình", null, ownerId);
        ReflectionTestUtils.setField(organization, "id", UUID.randomUUID());
        organization.addMember(managerId, OrganizationRole.MANAGER);
        organization.addMember(memberId, OrganizationRole.MEMBER);
        when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
    }

    private UUID id() {
        return organization.getId();
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(code));
    }

    @Test
    void outsiderDoesNotSeeTheOrganization() {
        assertCode(() -> service.get(UUID.randomUUID(), id()), "ORGANIZATION_NOT_FOUND");
    }

    @Test
    void managerInvitesMembersOnly() {
        UUID invited = UUID.randomUUID();
        when(users.findAssignable("new@x")).thenReturn(new UserContact(invited, "new@x", "Lan", "ACTIVE", false));

        assertThat(service.addMember(managerId, id(), new MemberRequest("new@x", OrganizationRole.MEMBER)).role())
                .isEqualTo(OrganizationRole.MEMBER);
        assertCode(() -> service.addMember(managerId, id(), new MemberRequest("x@x", OrganizationRole.MANAGER)),
                "ORGANIZATION_PERMISSION_DENIED");
        verify(users, never()).findAssignable("x@x");
    }

    @Test
    void memberCannotInvite() {
        assertCode(() -> service.addMember(memberId, id(), new MemberRequest("x@x", OrganizationRole.MEMBER)),
                "ORGANIZATION_PERMISSION_DENIED");
        verify(users, never()).findAssignable(anyString());
    }

    @Test
    void existingMemberIsNotAddedTwice() {
        when(users.findAssignable("m@x")).thenReturn(new UserContact(memberId, "m@x", "Minh", "ACTIVE", false));

        assertCode(() -> service.addMember(ownerId, id(), new MemberRequest("m@x", OrganizationRole.MEMBER)),
                "ALREADY_MEMBER");
    }

    @Test
    void onlyOwnerChangesRolesAndOwnerRoleNeedsATransfer() {
        assertCode(() -> service.changeMember(managerId, id(), memberId,
                new MemberUpdateRequest(OrganizationRole.MANAGER, false)), "NOT_ORGANIZATION_OWNER");
        assertCode(() -> service.changeMember(ownerId, id(), memberId,
                new MemberUpdateRequest(OrganizationRole.OWNER, false)), "INVALID_ROLE");

        assertThat(service.changeMember(ownerId, id(), memberId, new MemberUpdateRequest(OrganizationRole.MEMBER, true))
                .canCreateEvents()).isTrue();
    }

    @Test
    void managerRemovesMembersButNotOtherManagers() {
        UUID otherManager = UUID.randomUUID();
        organization.addMember(otherManager, OrganizationRole.MANAGER);

        assertCode(() -> service.removeMember(managerId, id(), otherManager), "ORGANIZATION_PERMISSION_DENIED");
        service.removeMember(managerId, id(), memberId);

        assertThat(organization.membershipOf(memberId)).isEmpty();
    }

    @Test
    void hostOfActiveEventsCannotLeave() {
        when(events.existsByOrganization_IdAndOrganizerIdAndStatusIn(eq(id()), eq(memberId), any())).thenReturn(true);

        assertCode(() -> service.removeMember(memberId, id(), memberId), "MEMBER_HOSTS_EVENTS");
        assertThat(organization.membershipOf(memberId)).isPresent();
    }

    @Test
    void ownerMustTransferBeforeLeaving() {
        assertCode(() -> service.removeMember(ownerId, id(), ownerId), "OWNER_CANNOT_LEAVE");
    }

    @Test
    void transferMakesTheOldOwnerAManager() {
        service.transferOwnership(ownerId, id(), memberId);

        assertThat(organization.requireMember(memberId).getRole()).isEqualTo(OrganizationRole.OWNER);
        assertThat(organization.requireMember(ownerId).getRole()).isEqualTo(OrganizationRole.MANAGER);
        verify(organizations).flush();
    }
}
