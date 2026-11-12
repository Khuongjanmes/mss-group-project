package com.openevent.payment.payments;

/** PAYOS hoặc VNPAY khi đã cấu hình; MOCK là cổng giả lập để chạy luồng khi chưa có tài khoản cổng thanh toán. */
public enum PaymentMethod {
    PAYOS, VNPAY, MOCK
}
