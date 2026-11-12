package com.openevent.order.orders;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.openevent.order.orders.dto.OrderSummaryResponse;

import lombok.RequiredArgsConstructor;

/** API cho Payment gọi qua Feign (KB-2). Gateway chặn /internal/** từ bên ngoài. */
@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderQueryService orders;

    @GetMapping("/{id}")
    public OrderSummaryResponse summary(@PathVariable UUID id) {
        return orders.summary(id);
    }
}
