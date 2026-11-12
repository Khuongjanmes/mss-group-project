package com.openevent.ticket.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.openevent.common.messaging.events.EventCancelled;
import com.openevent.common.messaging.events.OrderPaid;
import com.openevent.common.messaging.events.TicketIssued;
import com.openevent.common.messaging.events.TicketsCancelled;
import com.openevent.common.web.ApiException;
import com.openevent.common.web.PageResponse;
import com.openevent.ticket.client.EventDirectory;
import com.openevent.ticket.client.EventDirectory.EventWindow;
import com.openevent.ticket.client.IdentityClient;
import com.openevent.ticket.messaging.ProcessedMessages;
import com.openevent.ticket.reservations.Reservation;
import com.openevent.ticket.reservations.ReservationRepository;
import com.openevent.ticket.reservations.ReservationService;
import com.openevent.ticket.tickets.dto.AttendeeResponse;
import com.openevent.ticket.tickets.dto.TicketResponse;
import com.openevent.ticket.tickettypes.TicketType;
import com.openevent.ticket.tickettypes.TicketTypeRepository;

/** KB-4 (phát vé), KB-5 (hủy vé theo sự kiện), KB-3 + BR-09 (check-in). Không có DB, Event và Identity giả lập. */
class TicketServiceTest {

    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TicketTypeRepository types = mock(TicketTypeRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final ReservationService reservationService = mock(ReservationService.class);
    private final ProcessedMessages processed = mock(ProcessedMessages.class);
    private final EventDirectory events = mock(EventDirectory.class);
    private final IdentityClient identity = mock(IdentityClient.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final TicketService service = new TicketService(tickets, types, reservations, reservationService,
            processed, events, identity, publisher, new TransactionTemplate(mock(PlatformTransactionManager.class)));

    private final UUID eventId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();

    private OrderPaid paid() {
        OrderPaid event = new OrderPaid(UUID.randomUUID(), Instant.now(), orderId, ownerId, eventId);
        when(processed.markNew(event.messageId())).thenReturn(true);
        return event;
    }

    private Reservation reservation(int quantity) {
        return Reservation.hold(orderId, UUID.randomUUID(), quantity, BigDecimal.TEN, Instant.now().plusSeconds(60));
    }

    @Test
    void paidOrderConfirmsTheHoldAndIssuesOneTicketPerSeat() {
        OrderPaid event = paid();
        Reservation vip = reservation(2);
        Reservation standard = reservation(1);
        when(reservations.findAllWithLockByOrderId(orderId)).thenReturn(List.of(vip, standard));
        when(types.confirm(any(), anyInt())).thenReturn(1);
        when(tickets.save(any())).thenAnswer(call -> call.getArgument(0));

        service.issue(event);

        verify(types).confirm(vip.getTicketTypeId(), 2);
        verify(types).confirm(standard.getTicketTypeId(), 1);
        verify(tickets, times(3)).save(any());
        assertThat(vip.getStatus()).isEqualTo(Reservation.Status.CONFIRMED);
        ArgumentCaptor<TicketIssued> issued = ArgumentCaptor.forClass(TicketIssued.class);
        verify(publisher).publishEvent(issued.capture());
        assertThat(issued.getValue().ownerId()).isEqualTo(ownerId);
        assertThat(issued.getValue().ticketCodes()).hasSize(3).doesNotHaveDuplicates();
    }

    @Test
    void duplicateOrderPaidIssuesNothing() {
        OrderPaid event = new OrderPaid(UUID.randomUUID(), Instant.now(), orderId, ownerId, eventId);
        when(processed.markNew(event.messageId())).thenReturn(false);

        service.issue(event);

        verifyNoInteractions(reservations, tickets, publisher);
    }

    @Test
    void releasedHoldIsSoldAgainWhenSeatsRemain() {
        OrderPaid event = paid();
        Reservation late = reservation(1);
        late.release();
        when(reservations.findAllWithLockByOrderId(orderId)).thenReturn(List.of(late));
        when(types.sell(late.getTicketTypeId(), 1)).thenReturn(1);
        when(tickets.save(any())).thenAnswer(call -> call.getArgument(0));

        service.issue(event);

        verify(types, never()).confirm(any(), anyInt());
        verify(tickets).save(any());
        assertThat(late.getStatus()).isEqualTo(Reservation.Status.CONFIRMED);
    }

    @Test
    void releasedHoldWithNoSeatsLeftIssuesNothing() {
        OrderPaid event = paid();
        Reservation late = reservation(2);
        late.release();
        when(reservations.findAllWithLockByOrderId(orderId)).thenReturn(List.of(late));
        when(types.sell(late.getTicketTypeId(), 2)).thenReturn(0);

        service.issue(event);

        verify(tickets, never()).save(any());
        verify(publisher, never()).publishEvent(any(Object.class));
        assertThat(late.getStatus()).isEqualTo(Reservation.Status.RELEASED);
    }

    @Test
    void cancelledEventCancelsTicketsReleasesHoldsAndNotifiesOwners() {
        EventCancelled event = new EventCancelled(UUID.randomUUID(), Instant.now(), eventId);
        when(processed.markNew(event.messageId())).thenReturn(true);
        when(tickets.ownersWithIssuedTickets(eventId)).thenReturn(List.of(ownerId));

        service.cancelEvent(event);

        verify(tickets).cancelIssuedOfEvent(eventId);
        verify(reservationService).releaseEvent(eventId);
        ArgumentCaptor<TicketsCancelled> cancelled = ArgumentCaptor.forClass(TicketsCancelled.class);
        verify(publisher).publishEvent(cancelled.capture());
        assertThat(cancelled.getValue().ownerIds()).containsExactly(ownerId);
    }

    @Test
    void cancelledEventWithoutTicketsSendsNoEmail() {
        EventCancelled event = new EventCancelled(UUID.randomUUID(), Instant.now(), eventId);
        when(processed.markNew(event.messageId())).thenReturn(true);
        when(tickets.ownersWithIssuedTickets(eventId)).thenReturn(List.of());

        service.cancelEvent(event);

        verify(reservationService).releaseEvent(eventId);
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    /** Vé của sự kiện diễn ra trong khoảng {@code start}..{@code end}; Event trả cửa sổ đó cho người soát vé. */
    private Ticket ticketFor(Instant start, Instant end) {
        TicketType type = TicketType.create(eventId, "VIP", BigDecimal.TEN, 10, start.minusSeconds(86_400),
                start.minusSeconds(3600), UUID.randomUUID(), start, end);
        ReflectionTestUtils.setField(type, "id", UUID.randomUUID());
        Ticket ticket = Ticket.issue(type.getId(), orderId, ownerId, Instant.now());
        when(tickets.findByCode(ticket.getCode())).thenReturn(Optional.of(ticket));
        when(tickets.findWithLockByCode(ticket.getCode())).thenReturn(Optional.of(ticket));
        when(tickets.save(ticket)).thenReturn(ticket);
        when(types.findById(type.getId())).thenReturn(Optional.of(type));
        when(events.requireCheckIn(eq(eventId), eq(staffId), any()))
                .thenReturn(new EventWindow(type.getEventOrganizerId(), start, end, true));
        return ticket;
    }

    @Test
    void checksInOnTheEventDay() {
        Ticket ticket = ticketFor(Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));

        TicketResponse response = service.checkIn(staffId, "  " + ticket.getCode() + " ");

        assertThat(response.status()).isEqualTo(Ticket.Status.CHECKED_IN);
        assertThat(ticket.getCheckedInAt()).isNotNull();
        verify(types).refreshEventCache(eq(eventId), any(), any(), any());
    }

    @Test
    void refusesCheckInBeforeTheEventDay() {
        Ticket ticket = ticketFor(Instant.now().plus(Duration.ofDays(3)), Instant.now().plus(Duration.ofDays(3).plusHours(2)));

        assertThatThrownBy(() -> service.checkIn(staffId, ticket.getCode()))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("OUTSIDE_EVENT_DAYS"));
        assertThat(ticket.getStatus()).isEqualTo(Ticket.Status.ISSUED);
    }

    @Test
    void secondCheckInIsRefused() {
        Ticket ticket = ticketFor(Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        service.checkIn(staffId, ticket.getCode());

        assertThatThrownBy(() -> service.checkIn(staffId, ticket.getCode()))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("ALREADY_CHECKED_IN"));
    }

    @Test
    void unknownCodeIsNotFoundWithoutAskingEvent() {
        when(tickets.findByCode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkIn(staffId, "nope"))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("TICKET_NOT_FOUND"));
        verifyNoInteractions(events);
    }

    @Test
    void attendeesStillListedWhenIdentityIsDown() {
        Ticket ticket = Ticket.issue(UUID.randomUUID(), orderId, ownerId, Instant.now());
        when(tickets.findAllOfEvent(eq(eventId), any())).thenReturn(new PageImpl<>(List.of(ticket)));
        when(identity.getUsers(any())).thenThrow(new RuntimeException("identity down"));

        PageResponse<AttendeeResponse> page = service.attendees(staffId, eventId, 0, 20);

        verify(events).requireManage(eventId, staffId);
        assertThat(page.content()).singleElement().satisfies(attendee -> {
            assertThat(attendee.code()).isEqualTo(ticket.getCode());
            assertThat(attendee.ownerName()).isNull();
        });
    }
}
