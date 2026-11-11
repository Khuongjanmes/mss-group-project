package com.openevent.order.orders.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.openevent.order.orders.Order;
import com.openevent.order.orders.OrderItem;
import com.openevent.order.orders.OrderStatus;

public record OrderResponse(UUID id, UUID eventId, OrderStatus status, BigDecimal totalAmount, Instant expiresAt,
        Instant createdAt, List<Item> items) {

    public record Item(UUID ticketTypeId, int quantity, BigDecimal unitPrice) {

        static Item from(OrderItem item) {
            return new Item(item.getTicketTypeId(), item.getQuantity(), item.getUnitPrice());
        }
    }

    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getEventId(), order.getStatus(), order.getTotalAmount(),
                order.getExpiresAt(), order.getCreatedAt(), order.items().stream().map(Item::from).toList());
    }
}
