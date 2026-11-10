package com.openevent.payment.gateway;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.openevent.payment.payments.Payment;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * payOS (VietQR chuyển khoản vào tài khoản ngân hàng đã liên kết ở my.payos.vn). Chữ ký HMAC-SHA256 bằng Checksum
 * Key trên các cặp {@code key=value} sắp theo tên, nối bằng {@code &}, theo tài liệu payOS.
 */
@Component
@EnableConfigurationProperties(PaymentProperties.class)
public class PayOsGateway {

    /** payOS chỉ nhận mô tả tối đa 9 ký tự với tài khoản ngân hàng không liên kết qua payOS. */
    private static final String DESCRIPTION = "OpenEvent";
    private static final String SUCCESS = "00";

    private final PaymentProperties.PayOs config;
    private final JsonMapper json;
    private final RestClient http;

    public PayOsGateway(PaymentProperties properties, JsonMapper json) {
        this.config = properties.payos();
        this.json = json;
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(3));
        timeouts.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(timeouts)
                .baseUrl(config == null || config.baseUrl() == null ? "https://api-merchant.payos.vn" : config.baseUrl())
                .defaultHeader("x-client-id", config == null ? "" : config.clientId())
                .defaultHeader("x-api-key", config == null ? "" : config.apiKey())
                .build();
    }

    /** Kết quả payOS báo về, đã kiểm tra chữ ký. */
    public record Notice(String orderCode, long amount, boolean success) {
    }

    public boolean configured() {
        return config != null && config.configured();
    }

    /** Tạo link thanh toán; trả {@code checkoutUrl}. {@code transactionRef} của giao dịch là {@code orderCode}. */
    public String createLink(Payment payment, String returnUrl, String cancelUrl, Instant expiresAt) {
        long amount = payment.getAmount().toBigIntegerExact().longValueExact();
        String orderCode = payment.getTransactionRef();
        String signed = "amount=" + amount + "&cancelUrl=" + cancelUrl + "&description=" + DESCRIPTION
                + "&orderCode=" + orderCode + "&returnUrl=" + returnUrl;
        Map<String, Object> body = Map.of("orderCode", Long.parseLong(orderCode), "amount", amount,
                "description", DESCRIPTION, "returnUrl", returnUrl, "cancelUrl", cancelUrl,
                "expiredAt", expiresAt.getEpochSecond(), "signature", hmac(signed));
        JsonNode response = http.post().uri("/v2/payment-requests").body(body).retrieve().body(JsonNode.class);
        if (response == null || !SUCCESS.equals(response.path("code").asString())) {
            throw new IllegalStateException("payOS từ chối tạo link: " + (response == null ? null : response.path("desc")));
        }
        return response.path("data").path("checkoutUrl").asString();
    }

    /**
     * Hỏi trạng thái link khi chưa nhận được webhook (ví dụ chạy ở localhost, payOS không gọi vào được).
     * Gọi bằng API key qua HTTPS nên tin kết quả. Rỗng nếu chưa có kết quả cuối.
     */
    public Optional<Notice> status(String orderCode) {
        JsonNode response = http.get().uri("/v2/payment-requests/{id}", orderCode).retrieve().body(JsonNode.class);
        if (response == null || !SUCCESS.equals(response.path("code").asString())) {
            throw new IllegalStateException("payOS không trả trạng thái: " + (response == null ? null : response.path("desc")));
        }
        JsonNode data = response.path("data");
        return switch (data.path("status").asString()) {
            case "PAID" -> Optional.of(new Notice(orderCode, data.path("amountPaid").asLong(), true));
            case "CANCELLED", "EXPIRED" -> Optional.of(new Notice(orderCode, data.path("amount").asLong(), false));
            default -> Optional.empty();
        };
    }

    /** Webhook công khai: chỉ tin khi chữ ký trên {@code data} đúng. Rỗng nếu sai hoặc thiếu. */
    public Optional<Notice> verifyWebhook(JsonNode payload) {
        JsonNode data = payload.path("data");
        String given = payload.path("signature").asString("");
        if (!configured() || !data.isObject() || !MessageDigest.isEqual(
                signData(data).getBytes(StandardCharsets.US_ASCII), given.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        boolean success = payload.path("success").asBoolean(false) && SUCCESS.equals(payload.path("code").asString())
                && SUCCESS.equals(data.path("code").asString());
        return Optional.of(new Notice(data.path("orderCode").asString(), data.path("amount").asLong(), success));
    }

    /** Chữ ký của object {@code data}: null thành chuỗi rỗng, mảng thành JSON với khóa các phần tử đã sắp. */
    public String signData(JsonNode data) {
        return hmac(data.properties().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + value(e.getValue()))
                .collect(Collectors.joining("&")));
    }

    private String value(JsonNode node) {
        if (node == null || node.isNull() || "null".equals(node.asString(""))) {
            return "";
        }
        if (node.isArray()) {
            ArrayNode sorted = json.createArrayNode();
            node.forEach(item -> sorted.add(item.isObject() ? sortKeys(item) : item));
            return json.writeValueAsString(sorted);
        }
        return node.isObject() ? json.writeValueAsString(sortKeys(node)) : node.asString();
    }

    private ObjectNode sortKeys(JsonNode object) {
        ObjectNode sorted = json.createObjectNode();
        object.properties().stream().sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(e -> sorted.set(e.getKey(), e.getValue()));
        return sorted;
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.checksumKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Không tạo được chữ ký payOS", ex);
        }
    }
}
