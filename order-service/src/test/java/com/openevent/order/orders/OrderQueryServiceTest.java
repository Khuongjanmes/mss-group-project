package com.openevent.order.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.openevent.common.web.ApiException;
import com.openevent.order.orders.dto.OrderSummaryResponse;

class OrderQueryServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderQueryService service = new OrderQueryService(orders);

    @Test
    void summaryCarriesWhatPaymentNeeds() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(600);
        Order order = Order.open(userId, UUID.randomUUID(), expiresAt);
        when(orders.findById(orderId)).thenReturn(Optional.of(order));

        OrderSummaryResponse summary = service.summary(orderId);

        assertThat(summary.userId()).isEqualTo(userId);
        assertThat(summary.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(summary.totalAmount()).isZero();
        assertThat(summary.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void unknownOrderIsNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orders.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.summary(orderId)).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getStatus().value()).isEqualTo(404);
            assertThat(ex.getCode()).isEqualTo("ORDER_NOT_FOUND");
        });
    }
}
