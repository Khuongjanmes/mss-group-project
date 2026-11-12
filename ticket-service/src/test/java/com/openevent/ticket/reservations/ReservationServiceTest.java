package com.openevent.ticket.reservations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.openevent.common.messaging.events.OrderCancelled;
import com.openevent.common.web.ApiException;
import com.openevent.ticket.messaging.ProcessedMessages;
import com.openevent.ticket.reservations.dto.ReservationRequest;
import com.openevent.ticket.reservations.dto.ReservationResponse;
import com.openevent.ticket.tickettypes.TicketType;
import com.openevent.ticket.tickettypes.TicketTypeRepository;

/** KB-1 bước 5 (giữ vé tất cả hoặc không), BR-02 (nhả vé), BR-11 (bỏ message trùng). Không có DB. */
class ReservationServiceTest {

    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final TicketTypeRepository types = mock(TicketTypeRepository.class);
    private final ProcessedMessages processed = mock(ProcessedMessages.class);
    private final ReservationService service =
            new ReservationService(reservations, types, processed, Duration.ofMinutes(10));

    private final UUID eventId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    private TicketType type(UUID ofEvent, String price, Instant saleStart, Instant saleEnd) {
        TicketType type = TicketType.create(ofEvent, "Vé " + price, new BigDecimal(price), 100, saleStart, saleEnd,
                UUID.randomUUID(), Instant.now().plusSeconds(86_400), Instant.now().plusSeconds(90_000));
        ReflectionTestUtils.setField(type, "id", UUID.randomUUID());
        when(types.findById(type.getId())).thenReturn(Optional.of(type));
        return type;
    }

    private TicketType onSale(UUID ofEvent, String price) {
        return type(ofEvent, price, Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));
    }

    private ReservationRequest request(Object... typeAndQuantity) {
        List<ReservationRequest.Item> items = new ArrayList<>();
        for (int i = 0; i < typeAndQuantity.length; i += 2) {
            items.add(new ReservationRequest.Item(((TicketType) typeAndQuantity[i]).getId(), (int) typeAndQuantity[i + 1]));
        }
        return new ReservationRequest(orderId, items);
    }

    @Test
    void holdsEveryTypeAtItsCurrentPriceForTenMinutes() {
        TicketType vip = onSale(eventId, "300000");
        TicketType standard = onSale(eventId, "100000");
        when(types.reserve(any(), anyInt())).thenReturn(1);
        when(reservations.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        ReservationResponse response = service.reserve(request(vip, 1, standard, 2));

        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.expiresAt()).isBetween(Instant.now().plusSeconds(590), Instant.now().plusSeconds(600));
        assertThat(response.items()).extracting(ReservationResponse.Item::ticketTypeId, ReservationResponse.Item::quantity,
                ReservationResponse.Item::unitPrice)
                .containsExactlyInAnyOrder(
                        tuple(vip.getId(), 1, new BigDecimal("300000")),
                        tuple(standard.getId(), 2, new BigDecimal("100000")));
        verify(types).reserve(vip.getId(), 1);
        verify(types).reserve(standard.getId(), 2);
    }

    @Test
    void sameOrderTwiceReturnsWhatIsAlreadyHeld() {
        TicketType vip = onSale(eventId, "300000");
        when(reservations.findAllByOrderId(orderId)).thenReturn(List.of(Reservation.hold(orderId, vip.getId(), 1,
                vip.getPrice(), Instant.now().plusSeconds(300))));

        ReservationResponse response = service.reserve(request(vip, 3));

        assertThat(response.items()).singleElement().extracting(ReservationResponse.Item::quantity).isEqualTo(1);
        verify(types, never()).reserve(any(), anyInt());
    }

    @Test
    void notEnoughTicketsFailsTheWholeOrder() {
        TicketType vip = onSale(eventId, "300000");
        TicketType standard = onSale(eventId, "100000");
        when(types.reserve(any(), anyInt())).thenReturn(1);
        when(types.reserve(vip.getId(), 5)).thenReturn(0);

        assertThatThrownBy(() -> service.reserve(request(vip, 5, standard, 1)))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("SOLD_OUT"));
    }

    @Test
    void rejectsTheSameTypeListedTwice() {
        TicketType vip = onSale(eventId, "300000");

        assertThatThrownBy(() -> service.reserve(request(vip, 1, vip, 1)))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("DUPLICATE_TICKET_TYPE"));
        verify(types, never()).reserve(any(), anyInt());
    }

    @Test
    void rejectsTypesFromDifferentEvents() {
        when(types.reserve(any(), anyInt())).thenReturn(1);
        TicketType one = onSale(eventId, "100000");
        TicketType other = onSale(UUID.randomUUID(), "100000");

        assertThatThrownBy(() -> service.reserve(request(one, 1, other, 1)))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("MIXED_EVENTS"));
    }

    @Test
    void doesNotHoldOutsideTheSaleWindow() {
        TicketType closed = type(eventId, "100000", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));

        assertThatThrownBy(() -> service.reserve(request(closed, 1)))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("SALE_NOT_OPEN"));
        verify(types, never()).reserve(any(), anyInt());
    }

    @Test
    void cancelledOrderReturnsOnlyHeldSeats() {
        Reservation held = Reservation.hold(orderId, UUID.randomUUID(), 2, BigDecimal.TEN, Instant.now());
        Reservation confirmed = Reservation.hold(orderId, UUID.randomUUID(), 1, BigDecimal.TEN, Instant.now());
        confirmed.confirm();
        OrderCancelled event = new OrderCancelled(UUID.randomUUID(), Instant.now(), orderId, "EXPIRED");
        when(processed.markNew(event.messageId())).thenReturn(true);
        when(reservations.findAllWithLockByOrderId(orderId)).thenReturn(List.of(held, confirmed));
        when(types.release(any(), anyInt())).thenReturn(1);

        service.release(event);

        verify(types).release(held.getTicketTypeId(), 2);
        verify(types, never()).release(confirmed.getTicketTypeId(), 1);
        assertThat(held.getStatus()).isEqualTo(Reservation.Status.RELEASED);
        assertThat(confirmed.getStatus()).isEqualTo(Reservation.Status.CONFIRMED);
    }

    @Test
    void duplicateOrderCancelledIsIgnored() {
        OrderCancelled event = new OrderCancelled(UUID.randomUUID(), Instant.now(), orderId, "EXPIRED");
        when(processed.markNew(event.messageId())).thenReturn(false);

        service.release(event);

        verify(reservations, never()).findAllWithLockByOrderId(any());
        verify(types, never()).release(any(), anyInt());
    }

    @Test
    void expiryJobReleasesEveryExpiredHold() {
        Instant cutoff = Instant.now();
        List<Reservation> expired = List.of(
                Reservation.hold(orderId, UUID.randomUUID(), 1, BigDecimal.TEN, cutoff.minusSeconds(60)),
                Reservation.hold(UUID.randomUUID(), UUID.randomUUID(), 3, BigDecimal.TEN, cutoff.minusSeconds(60)));
        when(reservations.lockExpired(cutoff)).thenReturn(expired);
        when(types.release(any(), anyInt())).thenReturn(1);

        assertThat(service.releaseExpired(cutoff)).isEqualTo(2);
        assertThat(expired).allMatch(r -> r.getStatus() == Reservation.Status.RELEASED);
    }
}
