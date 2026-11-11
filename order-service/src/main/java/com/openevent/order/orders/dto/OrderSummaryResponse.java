package com.openevent.order.orders.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.openevent.order.orders.Order;
import com.openevent.order.orders.OrderStatus;

/**
 * KB-2: khớp {@code OrderClient.OrderSummary} của Payment. Payment tự kiểm tra đơn của người gọi, còn PENDING và
 * chưa quá {@code expiresAt} trước khi tạo giao dịch.
 */
public record OrderSummaryResponse(UUID id, UUID userId, BigDecimal totalAmount, OrderStatus status,
        Instant expiresAt) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(order.getId(), order.getUserId(), order.getTotalAmount(), order.getStatus(),
                order.getExpiresAt());
    }
}
