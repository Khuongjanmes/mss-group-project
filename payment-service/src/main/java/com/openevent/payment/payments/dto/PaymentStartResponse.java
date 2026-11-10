package com.openevent.payment.payments.dto;

/** {@code paymentUrl}: trang VNPay, hoặc trang cổng giả lập của SPA khi chưa cấu hình VNPay. */
public record PaymentStartResponse(PaymentResponse payment, String paymentUrl) {
}
