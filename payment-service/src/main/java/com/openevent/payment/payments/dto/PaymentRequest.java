package com.openevent.payment.payments.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Chỉ nhận mã đơn; số tiền lấy từ Order (KB-2). */
public record PaymentRequest(@NotNull UUID orderId) {
}
