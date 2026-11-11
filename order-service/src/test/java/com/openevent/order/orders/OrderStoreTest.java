package com.openevent.order.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.openevent.common.messaging.events.OrderCancelled;
import com.openevent.common.web.ApiException;

class OrderStoreTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final Quotas quotas = mock(Quotas.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final OrderStore store = new OrderStore(orders, quotas, publisher);

    private final UUID userId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    private Order pending(int tickets) {
        Order order = Order.open(userId, eventId, tickets, Instant.now().plusSeconds(600));
        when(orders.findLockedById(orderId)).thenReturn(Optional.of(order));
        return order;
    }

    private OrderCancelled published() {
        ArgumentCaptor<OrderCancelled> event = ArgumentCaptor.forClass(OrderCancelled.class);
        verify(publisher).publishEvent(event.capture());
        return event.getValue();
    }

    @Test
    void ownerCancelsPendingOrderAndTicketIsToldToRelease() {
        Order order = pending(3);

        assertThat(store.cancel(userId, orderId).status()).isEqualTo(OrderStatus.CANCELLED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(quotas).giveBack(userId, eventId, 3);
        assertThat(published().reason()).isEqualTo("USER_CANCELLED");
    }

    @Test
    void ordersOfOtherUsersLookMissing() {
        Order order = pending(1);

        assertThatThrownBy(() -> store.cancel(UUID.randomUUID(), orderId))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(404);
                    assertThat(ex.getCode()).isEqualTo("ORDER_NOT_FOUND");
                });
        assertThat(order.isPending()).isTrue();
        verify(quotas, never()).giveBack(any(), any(), anyInt());
    }

    @Test
    void onlyPendingOrdersCanBeCancelled() {
        pending(1).expire();

        assertThatThrownBy(() -> store.cancel(userId, orderId)).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getStatus().value()).isEqualTo(409);
            assertThat(ex.getCode()).isEqualTo("ORDER_NOT_PENDING");
        });
        verify(quotas, never()).giveBack(any(), any(), anyInt());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void overdueOrdersExpireGiveQuotaBackAndReleaseTickets() {
        Order first = Order.open(userId, eventId, 2, Instant.now().minusSeconds(1));
        Order second = Order.open(userId, eventId, 1, Instant.now().minusSeconds(1));
        Instant now = Instant.now();
        when(orders.lockExpired(now, 200)).thenReturn(List.of(first, second));

        assertThat(store.expireDue(now, 200)).isEqualTo(2);

        assertThat(first.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(second.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verify(quotas).giveBack(userId, eventId, 2);
        verify(quotas).giveBack(userId, eventId, 1);
        ArgumentCaptor<OrderCancelled> events = ArgumentCaptor.forClass(OrderCancelled.class);
        verify(publisher, times(2)).publishEvent(events.capture());
        assertThat(events.getAllValues()).extracting(OrderCancelled::reason).containsOnly("ORDER_EXPIRED");
    }

    @Test
    void abandonGivesBackTheCountTakenWhenOpening() {
        pending(4);

        store.abandon(orderId, "SOLD_OUT", false);

        verify(quotas).giveBack(userId, eventId, 4);
        verify(publisher, never()).publishEvent(any());
    }
}
