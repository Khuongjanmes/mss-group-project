package com.openevent.payment.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.openevent.common.messaging.events.OrderRefundRequested;
import com.openevent.common.messaging.events.PaymentFailed;
import com.openevent.common.messaging.events.PaymentSucceeded;
import com.openevent.common.security.AuthenticatedUser;
import com.openevent.common.web.ApiException;
import com.openevent.payment.client.OrderClient;
import com.openevent.payment.client.OrderClient.OrderSummary;
import com.openevent.payment.gateway.PayOsGateway;
import com.openevent.payment.gateway.PaymentProperties;
import com.openevent.payment.gateway.VnPayGateway;
import com.openevent.payment.payments.dto.PaymentStartResponse;

import feign.FeignException;
import tools.jackson.databind.json.JsonMapper;

/** KB-2 (tạo giao dịch, xử lý callback), KB-4 (phát kết quả), BR-10/BR-13 (hoàn tiền), BR-11. Không có DB. */
class PaymentServiceTest {

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final OrderClient orders = mock(OrderClient.class);
    private final PayOsGateway payOs = mock(PayOsGateway.class);
    private final VnPayGateway vnPay = mock(VnPayGateway.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PaymentService service = new PaymentService(payments, orders, payOs, vnPay,
            new PaymentProperties("http://web", null, null), publisher, jdbc,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

    private final UUID userId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final BigDecimal total = new BigDecimal("350000");

    private void order(UUID owner, String status, Instant expiresAt) {
        when(orders.getOrder(orderId)).thenReturn(new OrderSummary(orderId, owner, total, status, expiresAt));
    }

    private void pendingOrder() {
        order(userId, "PENDING", Instant.now().plusSeconds(300));
        when(payments.save(any())).thenAnswer(call -> {
            Payment payment = call.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
            return payment;
        });
    }

    private Payment payment(PaymentMethod method) {
        Payment payment = Payment.start(orderId, userId, total, method, "ref-" + UUID.randomUUID());
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        when(payments.findWithLockById(payment.getId())).thenReturn(Optional.of(payment));
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(payments.findByTransactionRef(payment.getTransactionRef())).thenReturn(Optional.of(payment));
        return payment;
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(code));
    }

    @Test
    void startsAMockPaymentForTheOrderTotal() {
        pendingOrder();

        PaymentStartResponse started = service.start(userId, orderId, "127.0.0.1");

        assertThat(started.payment().amount()).isEqualTo(total);
        assertThat(started.payment().method()).isEqualTo(PaymentMethod.MOCK);
        assertThat(started.payment().status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(started.paymentUrl()).isEqualTo("http://web/payments/" + started.payment().id() + "/mock");
    }

    @Test
    void someoneElsesOrderIsNotFound() {
        order(UUID.randomUUID(), "PENDING", Instant.now().plusSeconds(300));

        assertCode(() -> service.start(userId, orderId, "127.0.0.1"), "ORDER_NOT_FOUND");
        verify(payments, never()).save(any());
    }

    @Test
    void expiredOrderCannotBePaid() {
        order(userId, "PENDING", Instant.now().minusSeconds(1));

        assertCode(() -> service.start(userId, orderId, "127.0.0.1"), "ORDER_NOT_PAYABLE");
        verify(payments, never()).save(any());
    }

    @Test
    void paidOrderCannotBePaidTwice() {
        pendingOrder();
        when(payments.existsByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS)).thenReturn(true);

        assertCode(() -> service.start(userId, orderId, "127.0.0.1"), "ORDER_ALREADY_PAID");
        verify(payments, never()).save(any());
    }

    @Test
    void orderServiceDownRefusesInsteadOfGuessing() {
        when(orders.getOrder(orderId)).thenThrow(new RuntimeException("circuit open"));

        assertCode(() -> service.start(userId, orderId, "127.0.0.1"), "ORDER_UNAVAILABLE");
        verify(payments, never()).save(any());
    }

    @Test
    void orderMissingBehindTheCircuitBreakerIsNotFound() {
        when(orders.getOrder(orderId)).thenThrow(new RuntimeException("wrapped", mock(FeignException.NotFound.class)));

        assertCode(() -> service.start(userId, orderId, "127.0.0.1"), "ORDER_NOT_FOUND");
    }

    @Test
    void payOsDownClosesThePayment() {
        pendingOrder();
        when(payOs.configured()).thenReturn(true);
        when(payOs.createLink(any(), anyString(), anyString(), any())).thenThrow(new RuntimeException("timeout"));
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);

        assertCode(() -> service.start(userId, orderId, "127.0.0.1"), "PAYMENT_GATEWAY_UNAVAILABLE");

        verify(payments).save(saved.capture());
        verify(payments).findWithLockById(saved.getValue().getId());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void signedVnPaySuccessPaysAndPublishes() {
        Payment payment = payment(PaymentMethod.VNPAY);
        Map<String, String> query = Map.of("vnp_TxnRef", payment.getTransactionRef());
        when(vnPay.verify(query)).thenReturn(Optional.of(
                new VnPayGateway.Callback(payment.getTransactionRef(), total, true)));

        assertThat(service.handleVnPayReturn(query, "raw")).isEqualTo(payment.getId());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        ArgumentCaptor<PaymentSucceeded> event = ArgumentCaptor.forClass(PaymentSucceeded.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue().orderId()).isEqualTo(orderId);
        assertThat(event.getValue().amount()).isEqualTo(total);
        verify(jdbc).update(anyString(), any(), eq(payment.getId()), eq("raw"));
    }

    @Test
    void unsignedVnPayCallbackIsRejected() {
        when(vnPay.verify(any())).thenReturn(Optional.empty());

        assertCode(() -> service.handleVnPayReturn(Map.of(), "raw"), "INVALID_SIGNATURE");
        verify(payments, never()).findByTransactionRef(anyString());
    }

    @Test
    void wrongAmountIsRejectedAndPaymentStaysPending() {
        Payment payment = payment(PaymentMethod.VNPAY);
        when(vnPay.verify(any())).thenReturn(Optional.of(
                new VnPayGateway.Callback(payment.getTransactionRef(), new BigDecimal("1000"), true)));

        assertCode(() -> service.handleVnPayReturn(Map.of(), "raw"), "PAYMENT_AMOUNT_MISMATCH");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void repeatedCallbackPublishesOnlyOnce() {
        Payment payment = payment(PaymentMethod.VNPAY);
        when(vnPay.verify(any())).thenReturn(Optional.of(
                new VnPayGateway.Callback(payment.getTransactionRef(), total, true)));

        service.handleVnPayReturn(Map.of(), "first");
        service.handleVnPayReturn(Map.of(), "second");

        verify(publisher).publishEvent(any(PaymentSucceeded.class));
    }

    @Test
    void unsignedPayOsWebhookIsRejected() {
        when(payOs.verifyWebhook(any())).thenReturn(Optional.empty());

        assertCode(() -> service.handlePayOsWebhook(JsonMapper.builder().build().createObjectNode()),
                "INVALID_SIGNATURE");
    }

    @Test
    void failedMockPaymentPublishesPaymentFailed() {
        Payment payment = payment(PaymentMethod.MOCK);

        service.completeMock(userId, payment.getId(), false);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(publisher).publishEvent(any(PaymentFailed.class));
    }

    @Test
    void onlyThePayerCompletesAMockPayment() {
        Payment payment = payment(PaymentMethod.MOCK);

        assertCode(() -> service.completeMock(UUID.randomUUID(), payment.getId(), true), "PAYMENT_NOT_FOUND");
        assertThat(payment.isPending()).isTrue();
    }

    @Test
    void paymentIsHiddenFromOtherUsersButNotFromAdmin() {
        Payment payment = payment(PaymentMethod.MOCK);

        assertCode(() -> service.get(new AuthenticatedUser(UUID.randomUUID(), "x@x", Set.of("STUDENT")),
                payment.getId()), "PAYMENT_NOT_FOUND");
        assertThat(service.get(new AuthenticatedUser(UUID.randomUUID(), "a@x", Set.of("ADMIN")), payment.getId())
                .id()).isEqualTo(payment.getId());
    }

    @Test
    void refundRequestMarksSuccessfulPaymentsWithAShortenedReason() {
        Payment payment = payment(PaymentMethod.MOCK);
        payment.succeed(Instant.now());
        OrderRefundRequested event = new OrderRefundRequested(UUID.randomUUID(), Instant.now(), orderId, "x".repeat(600));
        when(jdbc.update(anyString(), eq(event.messageId()))).thenReturn(1);
        when(payments.findAllByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS)).thenReturn(List.of(payment));

        service.requestRefund(event);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(payment.getRefundReason()).hasSize(500);
    }

    @Test
    void duplicateRefundRequestIsIgnored() {
        OrderRefundRequested event = new OrderRefundRequested(UUID.randomUUID(), Instant.now(), orderId, "hủy");
        when(jdbc.update(anyString(), eq(event.messageId()))).thenReturn(0);

        service.requestRefund(event);

        verify(payments, never()).findAllByOrderIdAndStatus(any(), any());
    }
}
