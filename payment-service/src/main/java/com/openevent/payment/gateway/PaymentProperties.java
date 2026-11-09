package com.openevent.payment.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cổng được chọn theo thứ tự: payOS nếu đã cấu hình, rồi VNPay, cuối cùng là cổng giả lập (MOCK).
 *
 * @param frontendUrl SPA; sau khi thanh toán, trình duyệt được chuyển về {@code <frontendUrl>/payments/result}
 */
@ConfigurationProperties(prefix = "openevent.payment")
public record PaymentProperties(String frontendUrl, VnPay vnpay, PayOs payos) {

    public record VnPay(String tmnCode, String hashSecret, String payUrl, String returnUrl) {

        public boolean configured() {
            return tmnCode != null && !tmnCode.isBlank() && hashSecret != null && !hashSecret.isBlank();
        }
    }

    /** Lấy ở my.payos.vn, kênh thanh toán: Client ID, API Key, Checksum Key. */
    public record PayOs(String clientId, String apiKey, String checksumKey, String baseUrl) {

        public boolean configured() {
            return clientId != null && !clientId.isBlank() && apiKey != null && !apiKey.isBlank()
                    && checksumKey != null && !checksumKey.isBlank();
        }
    }
}
