package com.openevent.order.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.openevent.common.web.ApiException;
import com.openevent.order.client.EventClient;
import com.openevent.order.client.EventClient.EventSummary;
import com.openevent.order.client.IdentityClient;
import com.openevent.order.client.IdentityClient.UserContact;
import com.openevent.order.client.RemoteCalls;
import com.openevent.order.client.FeignErrors;
import com.openevent.order.client.TicketClient;
import com.openevent.order.client.TicketClient.ReservationRequest;
import com.openevent.order.client.TicketClient.ReservationResponse;
import com.openevent.order.client.TicketClient.ReservedItem;
import com.openevent.order.orders.dto.CreateOrderRequest;
import com.openevent.order.orders.dto.OrderResponse;

import tools.jackson.databind.json.JsonMapper;

/** KB-1: thứ tự kiểm tra và cách xử lý từng kiểu lỗi khi đặt vé. Không có DB: {@link OrderStore} được giả lập. */
class OrderServiceTest {

    private final IdentityClient identity = mock(IdentityClient.class);
    private final EventClient events = mock(EventClient.class);
    private final TicketClient tickets = mock(TicketClient.class);
    private final OrderStore store = mock(OrderStore.class);
    private final OrderService service = new OrderService(identity, events, tickets,
            new RemoteCalls(JsonMapper.builder().build(), 3, Duration.ZERO), store, Duration.ofMinutes(10));

    private final UUID userId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID vip = UUID.randomUUID();
    private final UUID standard = UUID.randomUUID();
    private final Instant holdUntil = Instant.now().plusSeconds(600);

    @BeforeEach
    void happyPath() {
        when(identity.getUser(userId)).thenReturn(new UserContact(userId, "a@x", "An", null, "ACTIVE"));
        when(events.getEvent(eventId)).thenReturn(event("PUBLISHED"));
        when(store.open(eq(userId), eq(eventId), anyInt(), any())).thenReturn(orderId);
        when(tickets.reserve(any())).thenReturn(reservation(eventId));
        when(store.reserved(eq(orderId), any())).thenReturn(new OrderResponse(orderId, eventId,
                OrderStatus.PENDING, new BigDecimal("2500000"), holdUntil, Instant.now(), List.of()));
    }

    private EventSummary event(String status) {
        return new EventSummary(eventId, UUID.randomUUID(), status, Instant.now().plusSeconds(86_400),
                Instant.now().plusSeconds(90_000));
    }

    private ReservationResponse reservation(UUID ofEvent) {
        return new ReservationResponse(orderId, ofEvent, holdUntil, List.of(
                new ReservedItem(vip, 2, new BigDecimal("1000000")),
                new ReservedItem(standard, 1, new BigDecimal("500000"))));
    }

    private CreateOrderRequest request() {
        return new CreateOrderRequest(eventId, List.of(new CreateOrderRequest.Item(vip, 2),
                new CreateOrderRequest.Item(standard, 1)));
    }

    private static void assertApiError(Throwable ex, int status, String code) {
        assertThat(ex).isInstanceOf(ApiException.class);
        assertThat(((ApiException) ex).getStatus().value()).isEqualTo(status);
        assertThat(((ApiException) ex).getCode()).isEqualTo(code);
    }

    @Test
    void createsPendingOrderAndReservesTicketsUnderTheNewOrderId() {
        OrderResponse created = service.create(userId, request());

        assertThat(created.id()).isEqualTo(orderId);
        verify(store).open(eq(userId), eq(eventId), eq(3), any());
        ArgumentCaptor<ReservationRequest> sent = ArgumentCaptor.forClass(ReservationRequest.class);
        verify(tickets).reserve(sent.capture());
        assertThat(sent.getValue().orderId()).isEqualTo(orderId);
        assertThat(sent.getValue().items()).extracting(TicketClient.ReservationItem::ticketTypeId)
                .containsExactly(vip, standard);
        verify(store).reserved(eq(orderId), eq(reservation(eventId)));
        verify(store, never()).abandon(any(), anyInt(), anyString(), any(Boolean.class));
    }

    @Test
    void orderExpiresNoLaterThanItsOwnHoldTime() {
        Instant before = Instant.now();
        service.create(userId, request());
        ArgumentCaptor<Instant> expiresAt = ArgumentCaptor.forClass(Instant.class);
        verify(store).open(any(), any(), anyInt(), expiresAt.capture());
        assertThat(expiresAt.getValue()).isBetween(before.plusSeconds(600), Instant.now().plusSeconds(600));
    }

    @Test
    void lockedAccountCannotOrder() {
        when(identity.getUser(userId)).thenReturn(new UserContact(userId, "a@x", "An", null, "LOCKED"));
        assertThatThrownBy(() -> service.create(userId, request()))
                .satisfies(ex -> assertApiError(ex, 403, "ACCOUNT_LOCKED"));
        verify(store, never()).open(any(), any(), anyInt(), any());
    }

    @Test
    void eventMustBePublished() {
        when(events.getEvent(eventId)).thenReturn(event("CANCELLED"));
        assertThatThrownBy(() -> service.create(userId, request()))
                .satisfies(ex -> assertApiError(ex, 409, "EVENT_NOT_ON_SALE"));
        verify(store, never()).open(any(), any(), anyInt(), any());
    }

    @Test
    void unknownEventIsNotFound() {
        when(events.getEvent(eventId)).thenThrow(FeignErrors.status(404, null));
        assertThatThrownBy(() -> service.create(userId, request()))
                .satisfies(ex -> assertApiError(ex, 404, "EVENT_NOT_FOUND"));
    }

    @Test
    void identityDownGives503WithoutCreatingOrder() {
        when(identity.getUser(userId)).thenThrow(new IllegalStateException("connection refused"));
        assertThatThrownBy(() -> service.create(userId, request()))
                .satisfies(ex -> assertApiError(ex, 503, "SERVICE_BUSY"));
        verify(identity, times(3)).getUser(userId);
        verify(store, never()).open(any(), any(), anyInt(), any());
    }

    @Test
    void sameTicketTypeTwiceIsRejectedBeforeCallingOtherServices() {
        CreateOrderRequest twice = new CreateOrderRequest(eventId,
                List.of(new CreateOrderRequest.Item(vip, 1), new CreateOrderRequest.Item(vip, 1)));
        assertThatThrownBy(() -> service.create(userId, twice))
                .satisfies(ex -> assertApiError(ex, 400, "DUPLICATE_TICKET_TYPE"));
        verify(identity, never()).getUser(any());
    }

    @Test
    void moreThanFiveTicketsInOneOrderExceedsQuota() {
        CreateOrderRequest six = new CreateOrderRequest(eventId,
                List.of(new CreateOrderRequest.Item(vip, 3), new CreateOrderRequest.Item(standard, 3)));
        assertThatThrownBy(() -> service.create(userId, six))
                .satisfies(ex -> assertApiError(ex, 409, "QUOTA_EXCEEDED"));
        verify(identity, never()).getUser(any());
    }

    @Test
    void soldOutCancelsOrderWithoutAskingTicketToRelease() {
        when(tickets.reserve(any())).thenThrow(FeignErrors.status(409,
                "{\"code\":\"SOLD_OUT\",\"message\":\"Loại vé \\\"VIP\\\" không còn đủ 2 vé\"}"));

        assertThatThrownBy(() -> service.create(userId, request()))
                .satisfies(ex -> assertApiError(ex, 409, "SOLD_OUT"))
                .hasMessageContaining("VIP");
        verify(tickets, times(1)).reserve(any());
        verify(store).abandon(orderId, 3, "SOLD_OUT", false);
        verify(store, never()).reserved(any(), any());
    }

    @Test
    void ticketDownIsRetriedThenOrderCancelledAndTicketAskedToRelease() {
        when(tickets.reserve(any())).thenThrow(FeignErrors.status(503, null));

        assertThatThrownBy(() -> service.create(userId, request()))
                .satisfies(ex -> assertApiError(ex, 503, "SERVICE_BUSY"));
        verify(tickets, times(3)).reserve(any());
        verify(store).abandon(orderId, 3, "TICKET_UNAVAILABLE", true);
    }

    @Test
    void ticketTypesOfAnotherEventAreRejectedAndReleased() {
        when(tickets.reserve(any())).thenReturn(reservation(UUID.randomUUID()));

        assertThatThrownBy(() -> service.create(userId, request()))
                .satisfies(ex -> assertApiError(ex, 400, "TICKET_EVENT_MISMATCH"));
        verify(store).abandon(orderId, 3, "TICKET_EVENT_MISMATCH", true);
        verify(store, never()).reserved(any(), any());
    }
}
