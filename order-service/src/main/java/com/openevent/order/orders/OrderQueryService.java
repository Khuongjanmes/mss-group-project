package com.openevent.order.orders;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.openevent.common.web.ApiException;
import com.openevent.order.orders.dto.OrderSummaryResponse;

import lombok.RequiredArgsConstructor;

/** Đọc đơn cho service khác và cho người dùng. */
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orders;

    /** KB-2 bước 2: số tiền và trạng thái đơn cho Payment. */
    @Transactional(readOnly = true)
    public OrderSummaryResponse summary(UUID orderId) {
        return orders.findById(orderId).map(OrderSummaryResponse::from).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn"));
    }
}
