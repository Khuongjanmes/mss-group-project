package com.openevent.payment.payments.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.openevent.payment.payments.Payment;
import com.openevent.payment.payments.PaymentMethod;
import com.openevent.payment.payments.PaymentStatus;

public record PaymentResponse(UUID id, UUID orderId, UUID userId, BigDecimal amount, PaymentMethod method,
        PaymentStatus status, String transactionRef, Instant paidAt, String refundReason, Instant createdAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getUserId(), payment.getAmount(),
                payment.getMethod(), payment.getStatus(), payment.getTransactionRef(), payment.getPaidAt(),
                payment.getRefundReason(), payment.getCreatedAt());
    }
}
