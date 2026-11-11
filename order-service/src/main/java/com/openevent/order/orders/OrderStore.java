package com.openevent.order.orders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.openevent.common.messaging.events.OrderCancelled;
import com.openevent.common.web.ApiException;
import com.openevent.order.client.TicketClient.ReservationResponse;
import com.openevent.order.orders.dto.OrderResponse;

import lombok.RequiredArgsConstructor;

/**
 * Các bước ghi DB của việc tạo đơn, mỗi bước một transaction ngắn. Lời gọi Identity, Event, Ticket nằm ngoài các
 * transaction này để không giữ kết nối DB trong lúc chờ mạng ({@link OrderService}).
 */
@Component
@RequiredArgsConstructor
public class OrderStore {

    private final OrderRepository orders;
    private final Quotas quotas;
    private final ApplicationEventPublisher publisher;

    /** BR-03 rồi tạo đơn PENDING; vượt giới hạn thì không tạo gì. */
    @Transactional
    public UUID open(UUID userId, UUID eventId, int ticketCount, Instant expiresAt) {
        if (!quotas.take(userId, eventId, ticketCount)) {
            throw new ApiException(HttpStatus.CONFLICT, "QUOTA_EXCEEDED",
                    "Mỗi tài khoản mua tối đa " + Quotas.MAX_PER_EVENT + " vé cho một sự kiện");
        }
        return orders.save(Order.open(userId, eventId, expiresAt)).getId();
    }

    /** Ticket đã giữ vé: ghi đơn giá và tổng tiền. */
    @Transactional
    public OrderResponse reserved(UUID orderId, ReservationResponse reservation) {
        Order order = require(orderId);
        List<OrderItem> items = reservation.items().stream()
                .map(item -> Order.item(item.ticketTypeId(), item.quantity(), item.unitPrice()))
                .toList();
        order.reserved(items, reservation.expiresAt());
        return OrderResponse.from(orders.saveAndFlush(order));
    }

    /**
     * Không giữ được vé: hủy đơn, trả lại quota. {@code releaseTickets}: Ticket có thể đã giữ vé (lỗi mạng, sai sự
     * kiện) nên phát {@code OrderCancelled} sau khi commit để Ticket nhả vé (KB-1 bước 7, KB-4 bước 5).
     */
    @Transactional
    public void abandon(UUID orderId, int ticketCount, String reason, boolean releaseTickets) {
        Order order = require(orderId);
        if (!order.isPending()) {
            return;
        }
        order.cancel();
        quotas.giveBack(order.getUserId(), order.getEventId(), ticketCount);
        if (releaseTickets) {
            publisher.publishEvent(new OrderCancelled(UUID.randomUUID(), Instant.now(), orderId, reason));
        }
    }

    private Order require(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn"));
    }
}
