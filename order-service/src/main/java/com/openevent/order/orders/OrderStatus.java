package com.openevent.order.orders;

/** Vòng đời đơn: PENDING → PAID, hoặc PENDING → CANCELLED / EXPIRED. */
public enum OrderStatus {
    PENDING, PAID, CANCELLED, EXPIRED
}
