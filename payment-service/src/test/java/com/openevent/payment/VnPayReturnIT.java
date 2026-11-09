package com.openevent.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.openevent.payment.gateway.VnPayGateway;

/** Đã cấu hình VNPay: URL thanh toán có chữ ký, callback chỉ được tin khi chữ ký đúng (KB-2). */
@TestPropertySource(properties = {
        "openevent.payment.vnpay.tmn-code=TESTCODE",
        "openevent.payment.vnpay.hash-secret=test-secret",
        "openevent.payment.frontend-url=http://spa.test"})
class VnPayReturnIT extends PaymentIntegrationTest {

    @Autowired
    VnPayGateway vnPay;

    private static Map<String, String> query(String url) {
        return Arrays.stream(URI.create(url).getRawQuery().split("&")).map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8), (a, b) -> a, TreeMap::new));
    }

    private Response vnPayReturn(Map<String, String> params) {
        String query = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return send("GET", "/api/payments/vnpay-return?" + query, null, null);
    }

    @Test
    void signedReturnCompletesPaymentOnceAndForgedReturnIsIgnored() {
        UUID orderId = pendingOrder(buyer, "250000");
        Response started = send("POST", "/api/payments", Map.of("orderId", orderId), buyer);
        String id = started.body().path("payment").path("id").asString();
        assertThat(started.body().path("payment").path("method").asString()).isEqualTo("VNPAY");

        Map<String, String> sent = query(started.text("paymentUrl"));
        assertThat(started.text("paymentUrl")).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(sent).containsEntry("vnp_Amount", "25000000").containsEntry("vnp_TmnCode", "TESTCODE");
        assertThat(vnPay.verify(sent)).isPresent();

        Map<String, String> back = new TreeMap<>(Map.of("vnp_TxnRef", sent.get("vnp_TxnRef"),
                "vnp_Amount", sent.get("vnp_Amount"), "vnp_ResponseCode", "00", "vnp_TransactionStatus", "00",
                "vnp_TmnCode", "TESTCODE", "vnp_OrderInfo", "Thanh toan don hang"));
        String signature = vnPay.sign(back);

        // Đổi số tiền sau khi ký: bị từ chối, giao dịch vẫn chờ.
        Map<String, String> forged = new TreeMap<>(back);
        forged.put("vnp_Amount", "100");
        forged.put("vnp_SecureHash", signature);
        Response rejected = vnPayReturn(forged);
        assertThat(rejected.status()).isEqualTo(302);
        assertThat(rejected.location()).isEqualTo("http://spa.test/payments/result?error=INVALID_SIGNATURE");
        assertThat(send("GET", "/api/payments/" + id, null, buyer).text("status")).isEqualTo("PENDING");

        back.put("vnp_SecureHash", signature.toUpperCase());
        Response returned = vnPayReturn(back);
        assertThat(returned.status()).isEqualTo(302);
        assertThat(returned.location()).isEqualTo("http://spa.test/payments/result?paymentId=" + id);
        assertThat(send("GET", "/api/payments/" + id, null, buyer).text("status")).isEqualTo("SUCCESS");

        // VNPay gọi lại lần nữa: vẫn chuyển về đúng trang, không xử lý hai lần.
        assertThat(vnPayReturn(back).location()).isEqualTo("http://spa.test/payments/result?paymentId=" + id);
        assertThat(send("GET", "/api/payments/" + id, null, buyer).text("status")).isEqualTo("SUCCESS");
    }
}
