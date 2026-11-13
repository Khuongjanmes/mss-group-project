package com.openevent.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import com.openevent.common.messaging.Messaging;
import com.openevent.common.messaging.events.OrderRefundRequested;
import com.openevent.common.messaging.events.PaymentFailed;
import com.openevent.common.messaging.events.PaymentSucceeded;
import com.openevent.payment.client.OrderClient.OrderSummary;

import feign.FeignException;
import feign.Request;

/** Cổng giả lập (chưa cấu hình VNPay): luồng KB-2, KB-4 và hoàn tiền BR-10, BR-13. */
class PaymentApiIT extends PaymentIntegrationTest {

    private Response start(UUID orderId, Caller caller) {
        return send("POST", "/api/payments", Map.of("orderId", orderId), caller);
    }

    private Response mock(String paymentId, boolean success, Caller caller) {
        return send("POST", "/api/payments/" + paymentId + "/mock-result", Map.of("success", success), caller);
    }

    private String pay(UUID orderId) {
        String id = start(orderId, buyer).body().path("payment").path("id").asString();
        assertThat(mock(id, true, buyer).text("status")).isEqualTo("SUCCESS");
        return id;
    }

    @Test
    void mockPaymentSucceedsOnceAndPublishesPaymentSucceeded() {
        String queue = probe("payment-succeeded-probe", Messaging.PAYMENT_SUCCEEDED);
        UUID orderId = pendingOrder(buyer, "150000");

        // Số tiền client gửi bị bỏ qua: lấy từ Order.
        Response started = send("POST", "/api/payments", Map.of("orderId", orderId, "amount", 1), buyer);
        assertThat(started.status()).isEqualTo(201);
        String id = started.body().path("payment").path("id").asString();
        assertThat(started.body().path("payment").path("amount").decimalValue()).isEqualByComparingTo("150000");
        assertThat(started.body().path("payment").path("method").asString()).isEqualTo("MOCK");
        assertThat(started.text("paymentUrl")).endsWith("/payments/" + id + "/mock");

        assertThat(mock(id, true, stranger).code()).isEqualTo("PAYMENT_NOT_FOUND");
        Response paid = mock(id, true, buyer);
        assertThat(paid.text("status")).isEqualTo("SUCCESS");
        assertThat(paid.text("paidAt")).isNotBlank();

        Object received = rabbit.receiveAndConvert(queue, Duration.ofSeconds(10).toMillis());
        assertThat(received).isInstanceOfSatisfying(PaymentSucceeded.class, event -> {
            assertThat(event.orderId()).isEqualTo(orderId);
            assertThat(event.amount()).isEqualByComparingTo("150000");
        });

        // Callback trùng (BR-11): ghi log nhưng không đổi trạng thái, không phát lại.
        assertThat(mock(id, false, buyer).text("status")).isEqualTo("SUCCESS");
        assertThat(rabbit.receiveAndConvert(queue, 1000)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from payment_logs where payment_id = ?::uuid", Integer.class, id))
                .isEqualTo(2);

        assertThat(start(orderId, buyer).code()).isEqualTo("ORDER_ALREADY_PAID");
    }

    @Test
    void failedPaymentPublishesPaymentFailedAndCanBeRetried() {
        String queue = probe("payment-failed-probe", Messaging.PAYMENT_FAILED);
        UUID orderId = pendingOrder(buyer, "90000");
        String id = start(orderId, buyer).body().path("payment").path("id").asString();

        assertThat(mock(id, false, buyer).text("status")).isEqualTo("FAILED");
        assertThat(rabbit.receiveAndConvert(queue, Duration.ofSeconds(10).toMillis()))
                .isInstanceOfSatisfying(PaymentFailed.class, event -> assertThat(event.orderId()).isEqualTo(orderId));

        assertThat(start(orderId, buyer).status()).isEqualTo(201);
    }

    @Test
    void onlyThePendingOrderOfTheCallerCanBePaid() {
        UUID orderId = pendingOrder(buyer, "50000");
        assertThat(start(orderId, stranger).code()).isEqualTo("ORDER_NOT_FOUND");
        assertThat(send("POST", "/api/payments", Map.of(), buyer).status()).isEqualTo(400);

        UUID expired = UUID.randomUUID();
        when(orders.getOrder(expired)).thenReturn(new OrderSummary(expired, buyer.id(), new BigDecimal("50000"),
                "PENDING", Instant.now().minusSeconds(1)));
        assertThat(start(expired, buyer).code()).isEqualTo("ORDER_NOT_PAYABLE");

        UUID cancelled = UUID.randomUUID();
        when(orders.getOrder(cancelled)).thenReturn(new OrderSummary(cancelled, buyer.id(), new BigDecimal("50000"),
                "CANCELLED", Instant.now().plusSeconds(600)));
        assertThat(start(cancelled, buyer).code()).isEqualTo("ORDER_NOT_PAYABLE");

        UUID missing = UUID.randomUUID();
        when(orders.getOrder(missing)).thenThrow(new FeignException.NotFound("không có", Request.create(
                Request.HttpMethod.GET, "/", Map.of(), null, StandardCharsets.UTF_8, null), null, Map.of()));
        assertThat(start(missing, buyer).code()).isEqualTo("ORDER_NOT_FOUND");

        // KB-2: Order không phản hồi thì từ chối, không tạo giao dịch.
        UUID down = UUID.randomUUID();
        when(orders.getOrder(down)).thenThrow(new IllegalStateException("order-service down"));
        Response unavailable = start(down, buyer);
        assertThat(unavailable.status()).isEqualTo(503);
        assertThat(unavailable.code()).isEqualTo("ORDER_UNAVAILABLE");
        assertThat(jdbc.queryForObject("select count(*) from payments where order_id = ?", Integer.class, down))
                .isZero();
    }

    @Test
    void refundRequestFromOrderIsHandledOnceThenAdminMarksRefunded() {
        UUID orderId = pendingOrder(buyer, "200000");
        String id = pay(orderId);
        String path = "/api/payments/" + id;

        OrderRefundRequested request = new OrderRefundRequested(UUID.randomUUID(), Instant.now(), orderId,
                "Sự kiện bị hủy");
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.ORDER_REFUND_REQUESTED, request);
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.ORDER_REFUND_REQUESTED, request);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(send("GET", path, null, buyer).text("status")).isEqualTo("REFUND_PENDING"));
        assertThat(send("GET", path, null, buyer).text("refundReason")).isEqualTo("Sự kiện bị hủy");
        // Trang Đơn của tôi: người mua thấy giao dịch của đơn mình, người khác không thấy.
        String mine = "/api/payments/my?orderId=" + orderId + "&orderId=" + UUID.randomUUID();
        assertThat(send("GET", mine, null, buyer).body().valueStream().map(p -> p.path("status").asString()))
                .containsExactly("REFUND_PENDING");
        assertThat(send("GET", mine, null, stranger).body().size()).isZero();
        assertThat(send("GET", "/api/payments/my", null, buyer).status()).isEqualTo(400);
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "select count(*) from processed_messages where message_id = ?", Integer.class, request.messageId()))
                .isEqualTo(1));

        assertThat(send("GET", path, null, stranger).code()).isEqualTo("PAYMENT_NOT_FOUND");
        assertThat(send("GET", path, null, admin).status()).isEqualTo(200);
        assertThat(send("GET", "/api/payments?status=REFUND_PENDING", null, buyer).status()).isEqualTo(403);
        assertThat(send("GET", "/api/payments?status=REFUND_PENDING&size=100", null, admin).body().path("content")
                .valueStream().map(payment -> payment.path("id").asString())).contains(id);

        assertThat(send("PUT", path + "/refunded", null, buyer).status()).isEqualTo(403);
        assertThat(send("PUT", path + "/refunded", null, admin).text("status")).isEqualTo("REFUNDED");
        assertThat(send("PUT", path + "/refunded", null, admin).code()).isEqualTo("PAYMENT_STATUS_INVALID");
    }
}
