package com.openevent.payment.gateway;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.openevent.payment.payments.Payment;

/**
 * VNPay phiên bản 2.1.0: tạo URL thanh toán và kiểm tra chữ ký HMAC-SHA512 khi VNPay chuyển trình duyệt về. Chữ ký
 * tính trên các tham số {@code vnp_*} có giá trị, sắp theo tên, giá trị mã hóa URL như mẫu của VNPay.
 */
@Component
@EnableConfigurationProperties(PaymentProperties.class)
public class VnPayGateway {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(VIETNAM);
    private static final String SUCCESS = "00";

    private final PaymentProperties.VnPay config;

    public VnPayGateway(PaymentProperties properties) {
        this.config = properties.vnpay();
    }

    /** Kết quả VNPay gửi về, đã kiểm tra chữ ký. */
    public record Callback(String transactionRef, BigDecimal amount, boolean success) {
    }

    public boolean configured() {
        return config != null && config.configured();
    }

    public String payUrl(Payment payment, String clientIp, Instant now, Instant expiresAt) {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", config.tmnCode());
        // VNPay nhận số tiền nhân 100, không có phần thập phân.
        params.put("vnp_Amount", payment.getAmount().movePointRight(2).toBigIntegerExact().toString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", payment.getTransactionRef());
        // VNPay chỉ nhận chữ không dấu, không ký tự đặc biệt.
        params.put("vnp_OrderInfo", "Thanh toan don hang " + payment.getOrderId().toString().replace("-", ""));
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", config.returnUrl());
        params.put("vnp_IpAddr", clientIp);
        params.put("vnp_CreateDate", TIME.format(now));
        params.put("vnp_ExpireDate", TIME.format(expiresAt));
        String query = canonical(params);
        return config.payUrl() + "?" + query + "&vnp_SecureHash=" + hmac(query);
    }

    /** Rỗng nếu thiếu hoặc sai chữ ký, hay tham số không đọc được: không tin dữ liệu đó. */
    public Optional<Callback> verify(Map<String, String> query) {
        String given = query.get("vnp_SecureHash");
        Map<String, String> fields = query.entrySet().stream()
                .filter(e -> e.getKey().startsWith("vnp_") && !e.getKey().startsWith("vnp_SecureHash"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new));
        if (!configured() || given == null || !MessageDigest.isEqual(
                sign(fields).getBytes(StandardCharsets.US_ASCII), given.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        try {
            boolean success = SUCCESS.equals(fields.get("vnp_ResponseCode"))
                    && SUCCESS.equals(fields.get("vnp_TransactionStatus"));
            return Optional.of(new Callback(fields.get("vnp_TxnRef"),
                    new BigDecimal(fields.get("vnp_Amount")).movePointLeft(2), success));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /** Chữ ký của một bộ tham số {@code vnp_*} (không gồm {@code vnp_SecureHash}). */
    public String sign(Map<String, String> fields) {
        return hmac(canonical(fields));
    }

    private static String canonical(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(config.hashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Không tạo được chữ ký VNPay", ex);
        }
    }
}
