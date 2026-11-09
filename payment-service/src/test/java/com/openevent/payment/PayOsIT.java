package com.openevent.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.openevent.common.messaging.Messaging;
import com.openevent.common.messaging.events.PaymentFailed;
import com.openevent.common.messaging.events.PaymentSucceeded;
import com.openevent.payment.gateway.PayOsGateway;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** payOS đã cấu hình, gọi vào server payOS giả: tạo link có chữ ký, webhook, và hỏi lại trạng thái. */
class PayOsIT extends PaymentIntegrationTest {

    private static final String CHECKSUM_KEY = "test-checksum-key";
    private static final HttpServer PAYOS;
    /** orderCode → trạng thái link mà payOS giả trả về. */
    private static final Map<String, String> LINK_STATUS = new ConcurrentHashMap<>();
    private static final Map<String, String> CREATED = new ConcurrentHashMap<>();
    private static volatile String createCode = "00";

    static {
        try {
            PAYOS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        PAYOS.createContext("/v2/payment-requests", PayOsIT::handle);
        PAYOS.start();
    }

    @DynamicPropertySource
    static void payOs(DynamicPropertyRegistry registry) {
        registry.add("openevent.payment.payos.client-id", () -> "client");
        registry.add("openevent.payment.payos.api-key", () -> "api-key");
        registry.add("openevent.payment.payos.checksum-key", () -> CHECKSUM_KEY);
        registry.add("openevent.payment.payos.base-url", () -> "http://127.0.0.1:" + PAYOS.getAddress().getPort());
        registry.add("openevent.payment.frontend-url", () -> "http://spa.test");
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String body;
        if (exchange.getRequestMethod().equals("POST")) {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String orderCode = request.replaceAll(".*\"orderCode\":(\\d+).*", "$1");
            CREATED.put(orderCode, request + "|" + exchange.getRequestHeaders().getFirst("x-client-id"));
            body = "{\"code\":\"" + createCode + "\",\"desc\":\"x\",\"data\":{\"checkoutUrl\":\"https://pay.payos.vn/web/"
                    + orderCode + "\"}}";
        } else {
            String orderCode = exchange.getRequestURI().getPath().replaceAll(".*/", "");
            body = "{\"code\":\"00\",\"desc\":\"success\",\"data\":{\"status\":\""
                    + LINK_STATUS.getOrDefault(orderCode, "PENDING") + "\",\"amount\":120000,\"amountPaid\":0}}";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Autowired
    PayOsGateway payOsGateway;

    private static String hmac(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(CHECKSUM_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private Response start(UUID orderId) {
        Response started = send("POST", "/api/payments", Map.of("orderId", orderId), buyer);
        assertThat(started.status()).isEqualTo(201);
        return started;
    }

    private ObjectNode webhook(String orderCode, long amount) {
        ObjectNode data = json.createObjectNode().put("orderCode", Long.parseLong(orderCode)).put("amount", amount)
                .put("description", "OpenEvent").put("code", "00").put("desc", "Thành công").putNull("reference");
        ObjectNode payload = json.createObjectNode().put("code", "00").put("desc", "success").put("success", true);
        payload.set("data", data);
        payload.put("signature", payOsGateway.signData(data));
        return payload;
    }

    @Test
    void linkIsSignedAndSignedWebhookCompletesPaymentOnce() throws Exception {
        String queue = probe("payos-succeeded-probe", Messaging.PAYMENT_SUCCEEDED);
        UUID orderId = pendingOrder(buyer, "120000");
        Response started = start(orderId);
        JsonNode payment = started.body().path("payment");
        String id = payment.path("id").asString();
        String orderCode = payment.path("transactionRef").asString();

        assertThat(payment.path("method").asString()).isEqualTo("PAYOS");
        assertThat(orderCode).containsOnlyDigits();
        assertThat(started.text("paymentUrl")).isEqualTo("https://pay.payos.vn/web/" + orderCode);

        // Chữ ký gửi payOS: amount, cancelUrl, description, orderCode, returnUrl theo thứ tự chữ cái.
        String result = "http://spa.test/payments/result?paymentId=" + id;
        String[] sent = CREATED.get(orderCode).split("\\|");
        assertThat(sent[1]).isEqualTo("client");
        assertThat(json.readTree(sent[0]).path("signature").asString()).isEqualTo(hmac("amount=120000&cancelUrl="
                + result + "&description=OpenEvent&orderCode=" + orderCode + "&returnUrl=" + result));

        ObjectNode forged = webhook(orderCode, 120000);
        ((ObjectNode) forged.path("data")).put("amount", 1);
        assertThat(send("POST", "/api/payments/payos-webhook", forged, null).code()).isEqualTo("INVALID_SIGNATURE");
        assertThat(send("GET", "/api/payments/" + id, null, buyer).text("status")).isEqualTo("PENDING");

        Response accepted = send("POST", "/api/payments/payos-webhook", webhook(orderCode, 120000), null);
        assertThat(accepted.status()).isEqualTo(200);
        assertThat(accepted.body().path("success").asBoolean()).isTrue();
        assertThat(send("GET", "/api/payments/" + id, null, buyer).text("status")).isEqualTo("SUCCESS");
        assertThat(rabbit.receiveAndConvert(queue, Duration.ofSeconds(10).toMillis()))
                .isInstanceOfSatisfying(PaymentSucceeded.class, event -> assertThat(event.orderId()).isEqualTo(orderId));

        // payOS gửi lại webhook: không xử lý hai lần.
        assertThat(send("POST", "/api/payments/payos-webhook", webhook(orderCode, 120000), null).status()).isEqualTo(200);
        assertThat(rabbit.receiveAndConvert(queue, 1000)).isNull();
    }

    @Test
    void webhookForUnknownOrderIsAcknowledged() {
        // payOS gửi dữ liệu thử (orderCode 123) khi khai báo URL webhook.
        assertThat(send("POST", "/api/payments/payos-webhook", webhook("123", 3000), null).status()).isEqualTo(200);
    }

    @Test
    void pendingPaymentAsksPayOsWhenWebhookCannotReachLocalhost() {
        String queue = probe("payos-failed-probe", Messaging.PAYMENT_FAILED);
        UUID orderId = pendingOrder(buyer, "120000");
        JsonNode payment = start(orderId).body().path("payment");
        String id = payment.path("id").asString();

        assertThat(send("GET", "/api/payments/" + id, null, buyer).text("status")).isEqualTo("PENDING");
        LINK_STATUS.put(payment.path("transactionRef").asString(), "CANCELLED");
        assertThat(send("GET", "/api/payments/" + id, null, buyer).text("status")).isEqualTo("FAILED");
        assertThat(rabbit.receiveAndConvert(queue, Duration.ofSeconds(10).toMillis()))
                .isInstanceOf(PaymentFailed.class);
    }

    @Test
    void payOsErrorClosesThePaymentWithoutTellingOrder() {
        UUID orderId = pendingOrder(buyer, "120000");
        createCode = "231";
        try {
            Response response = send("POST", "/api/payments", Map.of("orderId", orderId), buyer);
            assertThat(response.status()).isEqualTo(503);
            assertThat(response.code()).isEqualTo("PAYMENT_GATEWAY_UNAVAILABLE");
        } finally {
            createCode = "00";
        }
        assertThat(jdbc.queryForObject("select status from payments where order_id = ?", String.class, orderId))
                .isEqualTo("FAILED");
    }
}
