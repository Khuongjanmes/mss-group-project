package com.openevent.payment.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-service")
public interface OrderClient {

    record OrderSummary(UUID id, UUID userId, BigDecimal totalAmount, String status, Instant expiresAt) {
    }

    /** KB-2: lấy số tiền và kiểm tra đơn còn PENDING, chưa hết hạn. */
    @GetMapping("/internal/orders/{id}")
    OrderSummary getOrder(@PathVariable("id") UUID id);
}
