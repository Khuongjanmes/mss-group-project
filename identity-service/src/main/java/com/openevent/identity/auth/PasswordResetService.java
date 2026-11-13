package com.openevent.identity.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.openevent.common.messaging.events.PasswordResetRequested;
import com.openevent.common.web.ApiException;
import com.openevent.identity.user.User;
import com.openevent.identity.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Quên mật khẩu: tạo token dùng một lần (chỉ lưu bản băm), phát {@link PasswordResetRequested} để Notification gửi
 * email; đặt lại thì đổi mật khẩu và thu hồi mọi phiên.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final Duration TTL = Duration.ofMinutes(30);

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final RefreshTokenService refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher publisher;
    private final SecureRandom random = new SecureRandom();

    /**
     * Không cho biết email có tài khoản hay không: người gọi luôn nhận cùng một kết quả. Tài khoản chỉ đăng nhập
     * Google (không có mật khẩu) hoặc đang bị khóa thì không gửi gì.
     */
    @Transactional
    public void requestReset(String email) {
        users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(user -> user.getPasswordHash() != null && !user.isLocked())
                .ifPresentOrElse(this::issue, () -> log.info("Quên mật khẩu: email không có tài khoản dùng mật khẩu"));
    }

    private void issue(User user) {
        tokens.deleteUnusedOf(user.getId());
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(TTL);
        tokens.save(new PasswordResetToken(user, RefreshTokenService.hash(raw), expiresAt));
        publisher.publishEvent(new PasswordResetRequested(UUID.randomUUID(), now, user.getId(), user.getEmail(),
                user.getFullName(), raw, expiresAt));
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        Instant now = Instant.now();
        PasswordResetToken token = tokens.findWithLockByTokenHash(RefreshTokenService.hash(rawToken.trim()))
                .filter(found -> found.usableAt(now))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "RESET_TOKEN_INVALID",
                        "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"));
        User user = token.getUser();
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        token.markUsed(now);
        refreshTokens.revokeAll(user.getId());
    }
}
