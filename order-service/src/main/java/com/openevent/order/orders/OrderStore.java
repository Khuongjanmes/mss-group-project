package com.openevent.order.orders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.openevent.common.messaging.events.OrderCancelled;
import com.openevent.common.messaging.events.OrderPaid;
import com.openevent.common.messaging.events.OrderRefundRequested;
import com.openevent.common.messaging.events.PaymentFailed;
import com.openevent.common.messaging.events.PaymentSucceeded;
import com.openevent.common.web.ApiException;
import com.openevent.order.client.TicketClient.ReservationResponse;
import com.openevent.order.messaging.ProcessedMessages;
import com.openevent.order.orders.dto.OrderResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Các thay đổi trạng thái đơn, mỗi thay đổi một transaction ngắn. Lời gọi Identity, Event, Ticket nằm ngoài các
 * transaction này để không giữ kết nối DB trong lúc chờ mạng ({@link OrderService}). Đơn rời PENDING vì hủy, hết hạn
 * hoặc thanh toán thất bại thì trả lại quota (BR-03) và phát {@code OrderCancelled} để Ticket nhả vé (KB-4 bước 5).
 * Mọi message phát sau khi commit ({@code OrderEventRelay}). Đổi trạng thái luôn khóa dòng đơn trước, nên hủy đơn, job
 * hết hạn và kết quả thanh toán đến cùng lúc thì chỉ một bên thắng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStore {

    static final String USER_CANCELLED = "USER_CANCELLED";
    static final String ORDER_EXPIRED = "ORDER_EXPIRED";
    static final String PAYMENT_FAILED = "PAYMENT_FAILED";

    private final OrderRepository orders;
    private final Quotas quotas;
    private final ApplicationEventPublisher publisher;
    private final ProcessedMessages processed;

    /** BR-03 rồi tạo đơn PENDING; vượt giới hạn thì không tạo gì. */
    @Transactional
    public UUID open(UUID userId, UUID eventId, int ticketCount, Instant expiresAt) {
        if (!quotas.take(userId, eventId, ticketCount)) {
            throw new ApiException(HttpStatus.CONFLICT, "QUOTA_EXCEEDED",
                    "Mỗi tài khoản mua tối đa " + Quotas.MAX_PER_EVENT + " vé cho một sự kiện");
        }
        return orders.save(Order.open(userId, eventId, ticketCount, expiresAt)).getId();
    }

    /** Ticket đã giữ vé: ghi đơn giá và tổng tiền. */
    @Transactional
    public OrderResponse reserved(UUID orderId, ReservationResponse reservation) {
        Order order = orders.findById(orderId).orElseThrow(OrderStore::notFound);
        List<OrderItem> items = reservation.items().stream()
                .map(item -> Order.item(item.ticketTypeId(), item.quantity(), item.unitPrice()))
                .toList();
        order.reserved(items, reservation.expiresAt());
        return OrderResponse.from(orders.saveAndFlush(order));
    }

    /**
     * Không giữ được vé: hủy đơn, trả lại quota. {@code releaseTickets}: Ticket có thể đã giữ vé (lỗi mạng, sai sự
     * kiện) nên báo Ticket nhả (KB-1 bước 7); Ticket từ chối 4xx thì chưa giữ gì.
     */
    @Transactional
    public void abandon(UUID orderId, String reason, boolean releaseTickets) {
        Order order = orders.findLockedById(orderId).orElseThrow(OrderStore::notFound);
        if (!order.isPending()) {
            return;
        }
        order.cancel();
        giveBackQuota(order);
        if (releaseTickets) {
            publishCancelled(order, reason);
        }
    }

    /** BR-10: chỉ chủ đơn hủy được, và chỉ khi đơn còn PENDING. Đơn của người khác trả 404 như không tồn tại. */
    @Transactional
    public OrderResponse cancel(UUID userId, UUID orderId) {
        Order order = orders.findLockedById(orderId).filter(found -> found.getUserId().equals(userId))
                .orElseThrow(OrderStore::notFound);
        if (!order.isPending()) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_PENDING",
                    "Chỉ hủy được đơn đang chờ thanh toán (đơn đang " + order.getStatus() + ")");
        }
        order.cancel();
        giveBackQuota(order);
        publishCancelled(order, USER_CANCELLED);
        return OrderResponse.from(order);
    }

    /** BR-02: chuyển tối đa {@code limit} đơn PENDING quá hạn sang EXPIRED; trả số đơn đã chuyển. */
    @Transactional
    public int expireDue(Instant now, int limit) {
        List<Order> due = orders.lockExpired(now, limit);
        for (Order order : due) {
            order.expire();
            giveBackQuota(order);
            publishCancelled(order, ORDER_EXPIRED);
        }
        return due.size();
    }

    /**
     * KB-4 bước 2: đơn PENDING được trả trước hạn thì chuyển PAID và phát {@code OrderPaid} để Ticket phát hành vé.
     * BR-13: đơn đã EXPIRED, CANCELLED, hoặc được trả sau hạn, thì không phát vé mà yêu cầu Payment hoàn tiền.
     */
    @Transactional
    public void paymentSucceeded(PaymentSucceeded payment) {
        if (!processed.markNew(payment.messageId())) {
            return;
        }
        Order order = orders.findLockedById(payment.orderId()).orElse(null);
        if (order == null) {
            log.warn("PaymentSucceeded {} cho đơn {} không tồn tại", payment.paymentId(), payment.orderId());
            return;
        }
        Instant paidAt = payment.occurredAt() == null ? Instant.now() : payment.occurredAt();
        if (order.isPending() && !paidAt.isAfter(order.getExpiresAt())) {
            order.pay();
            publisher.publishEvent(new OrderPaid(UUID.randomUUID(), Instant.now(), order.getId(), order.getUserId(),
                    order.getEventId()));
            return;
        }
        if (order.getStatus() == OrderStatus.PAID) {
            // Payment hoàn tiền mọi giao dịch SUCCESS của đơn, nên yêu cầu hoàn ở đây sẽ hoàn cả giao dịch hợp lệ.
            log.warn("Đơn {} đã PAID, giao dịch {} trả thêm lần nữa cần xử lý tay", order.getId(),
                    payment.paymentId());
            return;
        }
        if (order.isPending()) {
            // Tiền về sau hạn nhưng job hết hạn chưa chạy tới: hết hạn ngay, như job sẽ làm.
            order.expire();
            giveBackQuota(order);
            publishCancelled(order, ORDER_EXPIRED);
        }
        publisher.publishEvent(new OrderRefundRequested(UUID.randomUUID(), Instant.now(), order.getId(),
                "ORDER_" + order.getStatus()));
    }

    /** KB-4 bước 5: thanh toán thất bại thì hủy đơn PENDING, trả quota và báo Ticket nhả vé. */
    @Transactional
    public void paymentFailed(PaymentFailed payment) {
        if (!processed.markNew(payment.messageId())) {
            return;
        }
        Order order = orders.findLockedById(payment.orderId()).orElse(null);
        if (order == null || !order.isPending()) {
            log.info("Bỏ qua PaymentFailed {} cho đơn {} ({})", payment.paymentId(), payment.orderId(),
                    order == null ? "không tồn tại" : order.getStatus());
            return;
        }
        order.cancel();
        giveBackQuota(order);
        publishCancelled(order, PAYMENT_FAILED);
    }

    private void giveBackQuota(Order order) {
        quotas.giveBack(order.getUserId(), order.getEventId(), order.getTicketCount());
    }

    private void publishCancelled(Order order, String reason) {
        publisher.publishEvent(new OrderCancelled(UUID.randomUUID(), Instant.now(), order.getId(), reason));
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn");
    }
}
