package com.openevent.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.openevent.payment.payments.Payment;
import com.openevent.payment.payments.PaymentMethod;

import tools.jackson.databind.json.JsonMapper;

/**
 * Gọi payOS thật bằng khóa trong biến môi trường; tạo một link 2.000đ (không chuyển tiền thì link tự hết hạn).
 * Chỉ chạy tay: {@code PAYOS_LIVE=true PAYOS_CLIENT_ID=... PAYOS_API_KEY=... PAYOS_CHECKSUM_KEY=...
 * ./mvnw -pl payment-service test -Dtest=PayOsLiveTest}.
 */
@EnabledIfEnvironmentVariable(named = "PAYOS_LIVE", matches = "true")
class PayOsLiveTest {

    private final PayOsGateway payOs = new PayOsGateway(new PaymentProperties("http://localhost:5173", null,
            new PaymentProperties.PayOs(System.getenv("PAYOS_CLIENT_ID"), System.getenv("PAYOS_API_KEY"),
                    System.getenv("PAYOS_CHECKSUM_KEY"), "https://api-merchant.payos.vn")),
            JsonMapper.builder().build());

    /** Sau khi đã chuyển tiền cho link: {@code PAYOS_PAID_ORDER_CODE=<orderCode>}. */
    @Test
    @EnabledIfEnvironmentVariable(named = "PAYOS_PAID_ORDER_CODE", matches = "\\d+")
    void readsPaidLink() {
        String orderCode = System.getenv("PAYOS_PAID_ORDER_CODE");
        System.out.println("payOS status: " + payOs.status(orderCode));

        assertThat(payOs.status(orderCode)).contains(new PayOsGateway.Notice(orderCode, 2000, true));
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "PAYOS_PAID_ORDER_CODE", matches = ".*")
    void createsRealLinkAndReadsItsStatus() {
        String orderCode = String.valueOf(System.currentTimeMillis() * 1000);
        Payment payment = Payment.start(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("2000"),
                PaymentMethod.PAYOS, orderCode);
        String result = "http://localhost:5173/payments/result";

        String checkoutUrl = payOs.createLink(payment, result, result, Instant.now().plus(Duration.ofMinutes(15)));
        System.out.println("payOS checkoutUrl: " + checkoutUrl + " (orderCode " + orderCode + ")");

        assertThat(checkoutUrl).startsWith("https://");
        assertThat(payOs.status(orderCode)).isEmpty();
    }
}
