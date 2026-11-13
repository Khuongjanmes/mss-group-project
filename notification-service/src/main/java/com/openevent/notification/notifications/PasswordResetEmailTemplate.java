package com.openevent.notification.notifications;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Email đặt lại mật khẩu: một nút dẫn tới trang đặt lại của SPA, link hết hạn sau 30 phút. */
final class PasswordResetEmailTemplate {

    static final String SUBJECT = "Đặt lại mật khẩu OpenEvent";
    private static final DateTimeFormatter EXPIRES = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private PasswordResetEmailTemplate() {
    }

    /** Nội dung ngắn lưu vào thông báo trong ứng dụng, không chứa link. */
    static String summary(Instant expiresAt) {
        return "Có yêu cầu đặt lại mật khẩu cho tài khoản của bạn; link trong email hết hạn lúc %s. Nếu không phải bạn, hãy bỏ qua."
                .formatted(EXPIRES.format(expiresAt));
    }

    static String html(String fullName, String token, Instant expiresAt, String frontendUrl) {
        StringBuilder html = EmailLayout.begin(SUBJECT, "Bấm nút trong email để đặt mật khẩu mới", "Đặt lại mật khẩu");
        EmailLayout.intro(html, fullName,
                "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản OpenEvent của bạn. Bấm nút dưới đây để đặt mật khẩu mới.",
                "Đặt lại mật khẩu", "");
        EmailLayout.buttons(html, frontendUrl, "/reset-password?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8),
                "Đặt mật khẩu mới", "/login", "Đăng nhập");
        EmailLayout.endSection(html);
        EmailLayout.notice(html, EmailLayout.DANGER_BG, "Lưu ý",
                "Link chỉ dùng được một lần và hết hạn lúc " + EXPIRES.format(expiresAt) + ".",
                "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này: mật khẩu hiện tại vẫn giữ nguyên.");
        return EmailLayout.end(html, "Email được gửi tự động từ OpenEvent. Vui lòng không trả lời email này.");
    }
}
