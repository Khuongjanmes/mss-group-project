package com.openevent.order.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Đơn đặt vé. Tạo ở PENDING trước khi giữ vé (để có {@code orderId} gửi Ticket), sau đó gắn các dòng vé và tổng tiền
 * khi Ticket giữ xong; giữ vé thất bại thì chuyển CANCELLED.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    /** Số vé đã trừ quota khi mở đơn (BR-03), trả lại khi đơn hủy hoặc hết hạn. */
    @Column(nullable = false, updatable = false)
    private int ticketCount;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    @Getter(AccessLevel.NONE)
    private List<OrderItem> items = new ArrayList<>();

    public static Order open(UUID userId, UUID eventId, int ticketCount, Instant expiresAt) {
        Order order = new Order();
        order.userId = userId;
        order.eventId = eventId;
        order.ticketCount = ticketCount;
        order.expiresAt = expiresAt;
        return order;
    }

    public List<OrderItem> items() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Ticket đã giữ vé: ghi đơn giá Ticket trả về và tính tổng. Hạn đơn không muộn hơn hạn giữ vé của Ticket, để đơn
     * hết hạn trước khi Ticket tự nhả vé.
     */
    public void reserved(List<OrderItem> reservedItems, Instant ticketHoldExpiresAt) {
        requirePending();
        items.clear();
        items.addAll(reservedItems);
        totalAmount = items.stream().map(OrderItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (ticketHoldExpiresAt != null && ticketHoldExpiresAt.isBefore(expiresAt)) {
            expiresAt = ticketHoldExpiresAt;
        }
    }

    public static OrderItem item(UUID ticketTypeId, int quantity, BigDecimal unitPrice) {
        return new OrderItem(ticketTypeId, quantity, unitPrice);
    }

    public boolean isPending() {
        return status == OrderStatus.PENDING;
    }

    public void cancel() {
        requirePending();
        status = OrderStatus.CANCELLED;
    }

    private void requirePending() {
        if (!isPending()) {
            throw new IllegalStateException("Đơn " + id + " không còn PENDING (" + status + ")");
        }
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
