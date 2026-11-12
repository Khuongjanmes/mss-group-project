package com.openevent.event.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.openevent.common.messaging.events.EventCancelled;
import com.openevent.common.security.AuthenticatedUser;
import com.openevent.common.web.ApiException;
import com.openevent.event.category.Category;
import com.openevent.event.category.CategoryService;
import com.openevent.event.client.UserDirectory;
import com.openevent.event.events.dto.EventAccessResponse;
import com.openevent.event.events.dto.EventRequest;
import com.openevent.event.events.dto.EventResponse;
import com.openevent.event.media.ImageStorage;
import com.openevent.event.organization.Membership;
import com.openevent.event.organization.Organization;
import com.openevent.event.organization.OrganizationRepository;
import com.openevent.event.organization.OrganizationRole;
import com.openevent.event.venue.Venue;
import com.openevent.event.venue.VenueService;

/** Vòng đời sự kiện (nháp → chờ duyệt → công bố → hủy), quyền quản lý (BR-06, BR-14), check-in (BR-09), KB-5. */
class EventServiceTest {

    private final EventRepository events = mock(EventRepository.class);
    private final CategoryService categories = mock(CategoryService.class);
    private final VenueService venues = mock(VenueService.class);
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final UserDirectory users = mock(UserDirectory.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final EventService service = new EventService(events, categories, venues, organizations, users,
            mock(ImageStorage.class), publisher, new TransactionTemplate(mock(PlatformTransactionManager.class)));

    private final UUID hostId = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID venueId = UUID.randomUUID();
    private final Instant start = Instant.now().plus(Duration.ofDays(7));

    @BeforeEach
    void catalog() {
        when(categories.require(categoryId)).thenReturn(Category.create("Hội thảo", "hoi-thao", null));
        when(venues.require(venueId)).thenReturn(Venue.create("Hội trường A", "1 Lê Lợi", "Đà Nẵng", 300));
        when(events.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private EventRequest request(Instant startTime, Instant endTime, UUID organizationId) {
        return new EventRequest("  Hội thảo AI  ", "<p>Mô tả</p>", null, categoryId, venueId, startTime, endTime,
                organizationId);
    }

    private Organization organization() {
        Organization organization = Organization.create("CLB Lập trình", null, UUID.randomUUID());
        ReflectionTestUtils.setField(organization, "id", UUID.randomUUID());
        when(organizations.getReferenceById(organization.getId())).thenReturn(organization);
        return organization;
    }

    private void member(Organization organization, UUID userId, OrganizationRole role, boolean canCreate) {
        when(organizations.findMembership(organization.getId(), userId))
                .thenReturn(Optional.of(new Membership(role, canCreate)));
    }

    /** Sự kiện đã lưu, ở trạng thái {@code status}. */
    private Event event(Organization organization, EventStatus status) {
        Event event = Event.draft(hostId, organization);
        event.updateDetails("Hội thảo AI", null, null, Category.create("Hội thảo", "hoi-thao", null),
                Venue.create("Hội trường A", "1 Lê Lợi", "Đà Nẵng", 300), start, start.plus(Duration.ofHours(3)),
                Instant.now());
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "status", status);
        when(events.findById(event.getId())).thenReturn(Optional.of(event));
        return event;
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(code));
    }

    @Test
    void creatorBecomesHostOfANewDraft() {
        EventResponse created = service.create(hostId, request(start, start.plus(Duration.ofHours(2)), null));

        assertThat(created.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(created.organizerId()).isEqualTo(hostId);
        assertThat(created.title()).isEqualTo("Hội thảo AI");
    }

    @Test
    void endMustComeAfterStart() {
        assertCode(() -> service.create(hostId, request(start, start.minusSeconds(60), null)), "INVALID_EVENT_TIME");
        verify(events, never()).save(any());
    }

    @Test
    void memberWithoutPermissionCannotCreateForTheOrganization() {
        Organization organization = organization();
        member(organization, hostId, OrganizationRole.MEMBER, false);

        assertCode(() -> service.create(hostId, request(start, start.plus(Duration.ofHours(2)), organization.getId())),
                "CANNOT_CREATE_EVENT");
        verify(events, never()).save(any());
    }

    @Test
    void outsiderCannotCreateForTheOrganization() {
        Organization organization = organization();
        when(organizations.findMembership(organization.getId(), stranger)).thenReturn(Optional.empty());

        assertCode(() -> service.create(stranger, request(start, start.plus(Duration.ofHours(2)), organization.getId())),
                "ORGANIZATION_NOT_FOUND");
    }

    @Test
    void organizationManagerEditsAnyEventButStrangerCannot() {
        Organization organization = organization();
        UUID managerId = UUID.randomUUID();
        member(organization, managerId, OrganizationRole.MANAGER, false);
        Event event = event(organization, EventStatus.DRAFT);

        service.update(managerId, event.getId(), request(start, start.plus(Duration.ofHours(5)), null));
        assertThat(event.getEndTime()).isEqualTo(start.plus(Duration.ofHours(5)));

        assertCode(() -> service.update(stranger, event.getId(), request(start, start.plusSeconds(60), null)),
                "NOT_EVENT_OWNER");
    }

    @Test
    void publishedEventCannotBeEditedOrDeleted() {
        Event event = event(null, EventStatus.PUBLISHED);

        assertCode(() -> service.update(hostId, event.getId(), request(start, start.plusSeconds(60), null)),
                "EVENT_STATUS_INVALID");
        assertCode(() -> service.delete(hostId, event.getId()), "EVENT_STATUS_INVALID");
        verify(events, never()).delete(any(Event.class));
    }

    @Test
    void submittedEventIsPublishedOnApproval() {
        Event event = event(null, EventStatus.DRAFT);

        assertThat(service.submit(hostId, event.getId()).status()).isEqualTo(EventStatus.PENDING_APPROVAL);
        assertThat(service.approve(event.getId()).status()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void eventThatAlreadyStartedIsNotApproved() {
        Event event = event(null, EventStatus.PENDING_APPROVAL);
        ReflectionTestUtils.setField(event, "startTime", Instant.now().minusSeconds(60));

        assertCode(() -> service.approve(event.getId()), "EVENT_ALREADY_STARTED");
        assertThat(event.getStatus()).isEqualTo(EventStatus.PENDING_APPROVAL);
    }

    @Test
    void rejectionKeepsTheTrimmedReason() {
        Event event = event(null, EventStatus.PENDING_APPROVAL);

        EventResponse rejected = service.reject(event.getId(), "  Thiếu mô tả  ");

        assertThat(rejected.status()).isEqualTo(EventStatus.REJECTED);
        assertThat(rejected.rejectReason()).isEqualTo("Thiếu mô tả");
    }

    @Test
    void cancellingAPublishedEventTellsTicketAndOrder() {
        Event event = event(null, EventStatus.PUBLISHED);

        service.cancel(new AuthenticatedUser(hostId, "h@x", Set.of("ORGANIZER")), event.getId());

        assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
        ArgumentCaptor<EventCancelled> cancelled = ArgumentCaptor.forClass(EventCancelled.class);
        verify(publisher).publishEvent(cancelled.capture());
        assertThat(cancelled.getValue().eventId()).isEqualTo(event.getId());
    }

    @Test
    void endedEventIsNotCancelled() {
        Event event = event(null, EventStatus.PUBLISHED);
        ReflectionTestUtils.setField(event, "endTime", Instant.now().minusSeconds(60));

        assertCode(() -> service.cancel(new AuthenticatedUser(UUID.randomUUID(), "a@x", Set.of("ADMIN")),
                event.getId()), "EVENT_ALREADY_ENDED");
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void draftIsHiddenFromStrangersButVisibleToStaff() {
        Event event = event(null, EventStatus.DRAFT);
        UUID staffId = UUID.randomUUID();
        event.addStaff(staffId);

        assertCode(() -> service.get(event.getId(), new AuthenticatedUser(stranger, "s@x", Set.of("STUDENT"))),
                "EVENT_NOT_FOUND");
        assertCode(() -> service.get(event.getId(), null), "EVENT_NOT_FOUND");
        assertThat(service.get(event.getId(), new AuthenticatedUser(staffId, "t@x", Set.of("STUDENT"))).id())
                .isEqualTo(event.getId());
    }

    @Test
    void staffMayCheckInButNotManage() {
        Organization organization = organization();
        Event event = event(organization, EventStatus.PUBLISHED);
        UUID staffId = UUID.randomUUID();
        UUID plainMember = UUID.randomUUID();
        event.addStaff(staffId);
        member(organization, plainMember, OrganizationRole.MEMBER, true);

        assertThat(service.access(event.getId(), staffId)).isEqualTo(new EventAccessResponse(false, true));
        assertThat(service.access(event.getId(), plainMember)).isEqualTo(new EventAccessResponse(false, false));
        assertThat(service.access(event.getId(), hostId)).isEqualTo(new EventAccessResponse(true, true));
    }

    @Test
    void personalEventKeepsItsHost() {
        Event event = event(null, EventStatus.PUBLISHED);

        assertCode(() -> service.assignHost(hostId, event.getId(), UUID.randomUUID()), "PERSONAL_EVENT");
        verifyNoInteractions(users);
    }

    @Test
    void newHostMustBelongToTheOrganization() {
        Event event = event(organization(), EventStatus.PUBLISHED);

        assertCode(() -> service.assignHost(hostId, event.getId(), stranger), "HOST_NOT_MEMBER");
        verifyNoInteractions(users);
        assertThat(event.getOrganizerId()).isEqualTo(hostId);
    }

    @Test
    void cancelledEventGetsNoNewStaff() {
        Event event = event(null, EventStatus.CANCELLED);

        assertCode(() -> service.addStaff(hostId, event.getId(), "staff@x"), "EVENT_STATUS_INVALID");
        verify(users, never()).findAssignable(anyString());
    }
}
