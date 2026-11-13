package com.openevent.ticket.tickettypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.openevent.common.web.ApiException;
import com.openevent.ticket.client.EventClient.EventSummary;
import com.openevent.ticket.client.EventDirectory;
import com.openevent.ticket.tickets.TicketRepository;
import com.openevent.ticket.tickettypes.dto.TicketTypeRequest;
import com.openevent.ticket.tickettypes.dto.TicketTypeResponse;

/** Organizer #4: quyền hỏi Event trước khi ghi; BR-07 (thời gian mở bán), BR-06 (không giảm dưới số đã bán). */
class TicketTypeServiceTest {

    private final TicketTypeRepository types = mock(TicketTypeRepository.class);
    private final EventDirectory events = mock(EventDirectory.class);
    private final TicketTypeService service = new TicketTypeService(types, mock(TicketRepository.class), events,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

    private final UUID organizerId = UUID.randomUUID();
    private final Instant eventStart = Instant.now().plus(Duration.ofDays(7));
    private final EventSummary event = new EventSummary(UUID.randomUUID(), organizerId, "PUBLISHED", eventStart,
            eventStart.plus(Duration.ofHours(3)));

    private TicketTypeRequest request(Instant saleStart, Instant saleEnd) {
        return new TicketTypeRequest(event.id(), "  Vé thường ", new BigDecimal("100000"), 50, saleStart, saleEnd);
    }

    private TicketTypeRequest valid() {
        return request(Instant.now(), eventStart.minus(Duration.ofDays(1)));
    }

    private TicketType existing() {
        TicketType type = TicketType.create(event.id(), "Vé thường", new BigDecimal("100000"), 50, Instant.now(),
                eventStart.minus(Duration.ofDays(1)), organizerId, event.startTime(), event.endTime());
        ReflectionTestUtils.setField(type, "id", UUID.randomUUID());
        when(types.findById(type.getId())).thenReturn(Optional.of(type));
        return type;
    }

    private static void assertCode(Throwable thrown, String code) {
        assertThat(thrown).isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(code));
    }

    @Test
    void organizerCreatesATicketTypeWithTrimmedName() {
        when(events.requireManage(event.id(), organizerId)).thenReturn(event);
        when(types.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        TicketTypeResponse created = service.create(organizerId, valid());

        assertThat(created.name()).isEqualTo("Vé thường");
        assertThat(created.available()).isEqualTo(50);
        verify(types).refreshEventCache(event.id(), organizerId, event.startTime(), event.endTime());
    }

    @Test
    void strangerCannotCreate() {
        UUID stranger = UUID.randomUUID();
        when(events.requireManage(event.id(), stranger))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Không có quyền"));

        assertCode(catchThrowable(() -> service.create(stranger, valid())), "FORBIDDEN");
        verify(types, never()).saveAndFlush(any());
    }

    @Test
    void saleMustEndBeforeTheEventStarts() {
        when(events.requireManage(event.id(), organizerId)).thenReturn(event);

        assertCode(catchThrowable(() -> service.create(organizerId, request(Instant.now(), eventStart.plusSeconds(60)))),
                "INVALID_SALE_TIME");
        verify(types, never()).saveAndFlush(any());
    }

    @Test
    void saleMustEndAfterItStarts() {
        when(events.requireManage(event.id(), organizerId)).thenReturn(event);
        Instant end = eventStart.minus(Duration.ofDays(2));

        assertCode(catchThrowable(() -> service.create(organizerId, request(end.plusSeconds(60), end))),
                "INVALID_SALE_TIME");
    }

    @Test
    void cancelledEventGetsNoNewTicketTypes() {
        when(events.requireManage(event.id(), organizerId)).thenReturn(new EventSummary(event.id(), organizerId,
                "CANCELLED", event.startTime(), event.endTime()));

        assertCode(catchThrowable(() -> service.create(organizerId, valid())), "EVENT_CLOSED");
    }

    @Test
    void cannotShrinkBelowSoldAndHeldSeats() {
        TicketType type = existing();
        when(events.requireManage(event.id(), organizerId)).thenReturn(event);
        when(types.resize(any(), anyInt())).thenReturn(0);

        assertCode(catchThrowable(() -> service.update(organizerId, type.getId(), valid())), "QUANTITY_BELOW_SOLD");
    }

    @Test
    void ticketTypeInUseIsNotDeleted() {
        TicketType type = existing();
        when(types.deleteUnused(type.getId())).thenReturn(0);

        assertCode(catchThrowable(() -> service.delete(organizerId, type.getId())), "TICKET_TYPE_IN_USE");
        verify(events).requireManage(event.id(), organizerId);
    }

    @Test
    void unknownTicketTypeIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(types.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(missing)).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
