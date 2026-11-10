package com.openevent.payment.payments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.openevent.common.web.ApiException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một lần thanh toán cho một đơn. Một đơn có thể có nhiều lần thử; chỉ một lần được SUCCESS. */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orderId;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    /** Lấy từ Order, không nhận từ client (KB-2). */
    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    /** Mã giao dịch gửi cổng thanh toán; duy nhất để callback trùng không xử lý hai lần (BR-11). */
    @Column(nullable = false, updatable = false)
    private String transactionRef;

    private Instant paidAt;

    private String refundReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** {@code transactionRef}: orderCode (số) với payOS, chuỗi chữ và số với VNPay và cổng giả lập. */
    public static Payment start(UUID orderId, UUID userId, BigDecimal amount, PaymentMethod method,
            String transactionRef) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.userId = userId;
        payment.amount = amount;
        payment.method = method;
        payment.transactionRef = transactionRef;
        return payment;
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public void succeed(Instant now) {
        requireStatus(PaymentStatus.PENDING, "Giao dịch đã được xử lý");
        status = PaymentStatus.SUCCESS;
        paidAt = now;
    }

    public void fail() {
        requireStatus(PaymentStatus.PENDING, "Giao dịch đã được xử lý");
        status = PaymentStatus.FAILED;
    }

    /** Hủy sự kiện hoặc thanh toán muộn trên đơn đã hết hạn (BR-10, BR-13). */
    public void requestRefund(String reason) {
        requireStatus(PaymentStatus.SUCCESS, "Chỉ hoàn tiền được giao dịch thành công");
        status = PaymentStatus.REFUND_PENDING;
        refundReason = reason;
    }

    /** Admin đã hoàn tiền thủ công qua cổng thanh toán. */
    public void markRefunded() {
        requireStatus(PaymentStatus.REFUND_PENDING, "Chỉ đánh dấu được giao dịch đang chờ hoàn tiền");
        status = PaymentStatus.REFUNDED;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    private void requireStatus(PaymentStatus expected, String message) {
        if (status != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_STATUS_INVALID", message);
        }
    }
}
