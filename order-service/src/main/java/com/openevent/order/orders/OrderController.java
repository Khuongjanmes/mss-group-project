package com.openevent.order.orders;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.openevent.common.security.AuthenticatedUser;
import com.openevent.common.web.PageResponse;
import com.openevent.order.orders.dto.CreateOrderRequest;
import com.openevent.order.orders.dto.OrderResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orders;
    private final OrderQueryService queries;
    private final OrderStore store;

    /** Đặt vé: tạo đơn PENDING và giữ vé 10 phút chờ thanh toán (KB-1). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody CreateOrderRequest request) {
        return orders.create(caller.id(), request);
    }

    /** Lịch sử đơn của tôi, mới nhất trước. */
    @GetMapping("/my")
    public PageResponse<OrderResponse> mine(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queries.mine(caller.id(), page, size);
    }

    /** Hủy đơn đang chờ thanh toán; Ticket nhả vé qua {@code OrderCancelled}. */
    @PutMapping("/{id}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return store.cancel(caller.id(), id);
    }
}
