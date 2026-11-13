package com.openevent.notification.notifications;

import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.openevent.common.messaging.events.PasswordResetRequested;
import com.openevent.notification.mail.EmailSender;

/**
 * Gửi email đặt lại mật khẩu; bỏ qua message trùng (BR-11). Chưa cấu hình mail hoặc FRONTEND_URL thì không gửi được
 * link: chỉ ghi log, không ghi token ra log.
 */
@Service
public class PasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetNotifier.class);

    private final NotificationRepository notifications;
    private final EmailSender email;
    private final String frontendUrl;

    public PasswordResetNotifier(NotificationRepository notifications, EmailSender email,
            @Value("${openevent.frontend-url:}") String frontendUrl) {
        this.notifications = notifications;
        this.email = email;
        this.frontendUrl = frontendUrl;
    }

    public void handle(PasswordResetRequested message) {
        if (notifications.existsByMessageIdAndUserId(message.messageId(), message.userId())) {
            log.info("Bỏ qua PasswordResetRequested {} đã xử lý", message.messageId());
            return;
        }
        if (!email.enabled() || frontendUrl == null || frontendUrl.isBlank()) {
            log.warn("Chưa cấu hình MAIL_HOST hoặc FRONTEND_URL: không gửi được email đặt lại mật khẩu cho {}",
                    message.userId());
            return;
        }
        email.send(message.email(), PasswordResetEmailTemplate.SUBJECT,
                PasswordResetEmailTemplate.html(message.fullName(), message.token(), message.expiresAt(), frontendUrl),
                Map.of());
        try {
            notifications.save(Notification.sent(message.messageId(), message.userId(), NotificationType.PASSWORD_RESET,
                    PasswordResetEmailTemplate.SUBJECT, PasswordResetEmailTemplate.summary(message.expiresAt()),
                    NotificationChannel.EMAIL, Instant.now()));
        } catch (DuplicateKeyException e) {
            log.info("PasswordResetRequested {} đã được lưu bởi lần xử lý khác", message.messageId());
        }
    }
}
