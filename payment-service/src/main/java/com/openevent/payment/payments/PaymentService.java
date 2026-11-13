package com.openevent.payment.payments;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.openevent.common.messaging.events.OrderRefundRequested;
import com.openevent.common.messaging.events.PaymentFailed;
import com.openevent.common.messaging.events.PaymentSucceeded;
import com.openevent.common.security.AuthenticatedUser;
import com.openevent.common.web.ApiException;
import com.openevent.common.web.PageResponse;
import com.openevent.payment.client.OrderClient;
import com.openevent.payment.client.OrderClient.OrderSummary;
import com.openevent.payment.payments.dto.PaymentResponse;
import com.openevent.payment.payments.dto.PaymentStartResponse;
import com.openevent.payment.gateway.PayOsGateway;
import com.openevent.payment.gateway.PayOsGateway.Notice;
import com.openevent.payment.gateway.PaymentProperties;
import com.openevent.payment.gateway.VnPayGateway;
import com.openevent.payment.gateway.VnPayGateway.Callback;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

/**
 * KB-2 và KB-4: tạo giao dịch từ đơn của Order, xử lý kết quả từ cổng thanh toán, phát PaymentSucceeded/Failed;
 * nhận yêu cầu hoàn tiền từ Order (BR-10, BR-13).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String ADMIN = "ADMIN";
    /** VNPay không nhận thời hạn quá xa; đơn cũng chỉ giữ vé 10 phút (BR-02). */
    private static final Duration MAX_PAY_WINDOW = Duration.ofMinutes(15);

    private final PaymentRepository payments;
    private final OrderClient orders;
    private final PayOsGateway payOs;
    private final VnPayGateway vnPay;
    private final PaymentProperties properties;
    private final ApplicationEventPublisher publisher;
    private final JdbcTemplate jdbc;
    /** Gọi Order ngoài transaction: không giữ kết nối DB trong lúc chờ mạng. */
    private final TransactionTemplate tx;

    public PaymentStartResponse start(UUID userId, UUID orderId, String clientIp) {
        OrderSummary order = requirePayableOrder(userId, orderId);
        PaymentMethod method = payOs.configured() ? PaymentMethod.PAYOS
                : vnPay.configured() ? PaymentMethod.VNPAY : PaymentMethod.MOCK;
        Payment payment = tx.execute(status -> {
            if (payments.existsByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS)) {
                throw new ApiException(HttpStatus.CONFLICT, "ORDER_ALREADY_PAID", "Đơn này đã được thanh toán");
            }
            return payments.save(Payment.start(orderId, userId, order.totalAmount(), method, newReference(method)));
        });
        Instant now = Instant.now();
        Instant deadline = now.plus(MAX_PAY_WINDOW);
        if (order.expiresAt() != null && order.expiresAt().isBefore(deadline)) {
            deadline = order.expiresAt();
        }
        String url = switch (method) {
            case PAYOS -> createPayOsLink(payment, deadline);
            case VNPAY -> vnPay.payUrl(payment, clientIp, now, deadline);
            case MOCK -> properties.frontendUrl() + "/payments/" + payment.getId() + "/mock";
        };
        return new PaymentStartResponse(PaymentResponse.from(payment), url);
    }

    /**
     * Người trả tiền hoặc Admin; người khác nhận 404. Giao dịch payOS còn chờ thì hỏi lại payOS, vì webhook không
     * tới được khi chạy ở máy cá nhân.
     */
    public PaymentResponse get(AuthenticatedUser caller, UUID id) {
        Payment payment = tx.execute(status -> payments.findById(id)
                .filter(p -> p.getUserId().equals(caller.id()) || caller.hasRole(ADMIN))
                .orElseThrow(PaymentService::notFound));
        if (payment.getMethod() == PaymentMethod.PAYOS && payment.isPending() && payOs.configured()) {
            syncWithPayOs(payment.getTransactionRef());
            return tx.execute(status -> PaymentResponse.from(payments.findById(id).orElseThrow()));
        }
        return PaymentResponse.from(payment);
    }

    /** Webhook payOS (công khai): chỉ tin dữ liệu có chữ ký đúng. Giao dịch không có (ví dụ dữ liệu thử khi đăng ký webhook) thì bỏ qua. */
    public void handlePayOsWebhook(JsonNode payload) {
        Notice notice = payOs.verifyWebhook(payload).orElseThrow(() -> {
            log.warn("Webhook payOS sai chữ ký: {}", payload);
            return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SIGNATURE", "Chữ ký payOS không hợp lệ");
        });
        applyPayOs(notice, payload.toString());
    }

    /** Giao dịch của chính người gọi theo đơn, mới nhất trước: trang Đơn của tôi hiện trạng thái hoàn tiền. */
    @Transactional(readOnly = true)
    public List<PaymentResponse> mine(UUID userId, List<UUID> orderIds) {
        return payments.findAllByUserIdAndOrderIdInOrderByCreatedAtDesc(userId, orderIds).stream()
                .map(PaymentResponse::from).toList();
    }

    /** Admin #4: mới nhất trước, lọc theo trạng thái. */
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> search(PaymentStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        return PageResponse.from((status == null ? payments.findAll(pageable)
                : payments.findAllByStatus(status, pageable)).map(PaymentResponse::from));
    }

    /** VNPay chuyển trình duyệt về; endpoint công khai nên chỉ tin dữ liệu có chữ ký đúng (KB-2). */
    public UUID handleVnPayReturn(Map<String, String> query, String raw) {
        Callback callback = vnPay.verify(query).orElseThrow(() -> {
            log.warn("Callback VNPay sai chữ ký: {}", raw);
            return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SIGNATURE", "Chữ ký VNPay không hợp lệ");
        });
        return tx.execute(status -> {
            Payment payment = payments.findByTransactionRef(callback.transactionRef())
                    .orElseThrow(PaymentService::notFound);
            complete(payment, callback.success(), callback.amount(), raw);
            return payment.getId();
        });
    }

    /** Cổng giả lập: chính người trả tiền chọn thành công hoặc thất bại. */
    @Transactional
    public PaymentResponse completeMock(UUID callerId, UUID id, boolean success) {
        Payment payment = payments.findWithLockById(id).filter(p -> p.getUserId().equals(callerId))
                .orElseThrow(PaymentService::notFound);
        if (payment.getMethod() != PaymentMethod.MOCK) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_MOCK_PAYMENT", "Giao dịch này không dùng cổng giả lập");
        }
        complete(payment, success, payment.getAmount(), "MOCK success=" + success);
        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse markRefunded(UUID id) {
        Payment payment = payments.findWithLockById(id).orElseThrow(PaymentService::notFound);
        payment.markRefunded();
        return PaymentResponse.from(payment);
    }

    /** BR-10, BR-13; bỏ qua message trùng theo messageId (BR-11). */
    @Transactional
    public void requestRefund(OrderRefundRequested event) {
        int inserted = jdbc.update(
                "insert into processed_messages (message_id, processed_at) values (?, now()) on conflict do nothing",
                event.messageId());
        if (inserted == 0) {
            return;
        }
        List<Payment> paid = payments.findAllByOrderIdAndStatus(event.orderId(), PaymentStatus.SUCCESS);
        if (paid.isEmpty()) {
            log.warn("Yêu cầu hoàn tiền cho đơn {} nhưng không có giao dịch thành công", event.orderId());
        }
        String reason = event.reason() == null || event.reason().length() <= 500 ? event.reason()
                : event.reason().substring(0, 500);
        paid.forEach(payment -> payment.requestRefund(reason));
    }

    /** Ghi log mọi callback; callback trùng của giao dịch đã xử lý thì không làm gì thêm (BR-11). */
    private void complete(Payment payment, boolean success, BigDecimal amount, String raw) {
        jdbc.update("insert into payment_logs (id, payment_id, raw_callback, created_at) values (?, ?, ?, now())",
                UUID.randomUUID(), payment.getId(), raw);
        if (!payment.isPending()) {
            return;
        }
        if (payment.getAmount().compareTo(amount) != 0) {
            log.error("Số tiền callback {} khác giao dịch {} ({})", amount, payment.getId(), payment.getAmount());
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_AMOUNT_MISMATCH", "Số tiền thanh toán không khớp");
        }
        Instant now = Instant.now();
        if (success) {
            payment.succeed(now);
            publisher.publishEvent(new PaymentSucceeded(UUID.randomUUID(), now, payment.getId(), payment.getOrderId(),
                    payment.getAmount(), payment.getTransactionRef()));
        } else {
            payment.fail();
            publisher.publishEvent(new PaymentFailed(UUID.randomUUID(), now, payment.getId(), payment.getOrderId(),
                    "Thanh toán không thành công"));
        }
    }

    private void syncWithPayOs(String orderCode) {
        try {
            payOs.status(orderCode).ifPresent(notice -> applyPayOs(notice, "SYNC " + notice));
        } catch (RuntimeException ex) {
            log.warn("Không hỏi được trạng thái payOS cho {}: {}", orderCode, ex.toString());
        }
    }

    private void applyPayOs(Notice notice, String raw) {
        tx.executeWithoutResult(status -> payments.findByTransactionRef(notice.orderCode()).ifPresentOrElse(
                payment -> complete(payment, notice.success(), BigDecimal.valueOf(notice.amount()), raw),
                () -> log.info("payOS báo orderCode {} không có trong hệ thống", notice.orderCode())));
    }

    /** payOS lỗi thì đóng giao dịch (không phát PaymentFailed: đơn vẫn còn hạn để thử lại). */
    private String createPayOsLink(Payment payment, Instant deadline) {
        String result = properties.frontendUrl() + "/payments/result?paymentId=" + payment.getId();
        try {
            return payOs.createLink(payment, result, result, deadline);
        } catch (RuntimeException ex) {
            log.warn("Không tạo được link payOS cho giao dịch {}: {}", payment.getId(), ex.toString());
            tx.executeWithoutResult(status -> payments.findWithLockById(payment.getId()).ifPresent(Payment::fail));
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_GATEWAY_UNAVAILABLE",
                    "Cổng thanh toán đang bận, vui lòng thử lại");
        }
    }

    /** payOS cần orderCode là số, không trùng trên cả tài khoản payOS kể cả khi xóa DB; VNPay chỉ nhận chữ và số. */
    private static String newReference(PaymentMethod method) {
        return method == PaymentMethod.PAYOS
                ? String.valueOf(System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000))
                : UUID.randomUUID().toString().replace("-", "");
    }

    /** KB-2: số tiền và trạng thái lấy từ Order; Order không phản hồi thì từ chối, không tạo giao dịch. */
    private OrderSummary requirePayableOrder(UUID userId, UUID orderId) {
        OrderSummary order;
        try {
            order = orders.getOrder(orderId);
        } catch (RuntimeException ex) {
            if (causedBy(ex, FeignException.NotFound.class)) {
                throw orderNotFound();
            }
            log.warn("Order không phản hồi khi tạo thanh toán cho đơn {}: {}", orderId, ex.toString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ORDER_UNAVAILABLE",
                    "Chưa kiểm tra được đơn hàng, vui lòng thử lại");
        }
        if (order == null || !userId.equals(order.userId())) {
            throw orderNotFound();
        }
        boolean expired = order.expiresAt() != null && !order.expiresAt().isAfter(Instant.now());
        if (!"PENDING".equals(order.status()) || expired) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_PAYABLE",
                    "Đơn đã hết hạn, đã hủy hoặc đã thanh toán");
        }
        return order;
    }

    /** Feign bọc lỗi trong ngoại lệ của circuit breaker, nên tìm theo chuỗi nguyên nhân. */
    private static boolean causedBy(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Không tìm thấy giao dịch");
    }

    private static ApiException orderNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng");
    }
}
