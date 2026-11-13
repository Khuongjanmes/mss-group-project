package com.openevent.common.messaging.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Identity phát khi có yêu cầu quên mật khẩu; Notification gửi email chứa link đặt lại. {@code token} là bản gốc
 * (Identity chỉ lưu bản băm), dùng một lần, hết hạn lúc {@code expiresAt}.
 */
public record PasswordResetRequested(UUID messageId, Instant occurredAt, UUID userId, String email, String fullName,
        String token, Instant expiresAt) implements DomainEvent {
}
