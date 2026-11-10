package com.openevent.ticket.reservations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Vé đang giữ cho một đơn, theo từng loại vé; hết hạn sau 10 phút nếu chưa thanh toán (BR-02). */
@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    public enum Status {
        HELD, CONFIRMED, RELEASED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orderId;

    @Column(nullable = false, updatable = false)
    private UUID ticketTypeId;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(nullable = false, updatable = false)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.HELD;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    public static Reservation hold(UUID orderId, UUID ticketTypeId, int quantity, BigDecimal unitPrice,
            Instant expiresAt) {
        Reservation reservation = new Reservation();
        reservation.orderId = orderId;
        reservation.ticketTypeId = ticketTypeId;
        reservation.quantity = quantity;
        reservation.unitPrice = unitPrice;
        reservation.expiresAt = expiresAt;
        return reservation;
    }

    public boolean isHeld() {
        return status == Status.HELD;
    }

    public void confirm() {
        status = Status.CONFIRMED;
    }

    public void release() {
        status = Status.RELEASED;
    }
}
