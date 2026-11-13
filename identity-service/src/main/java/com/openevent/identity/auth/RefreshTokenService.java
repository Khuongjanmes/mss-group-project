package com.openevent.identity.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.openevent.common.web.ApiException;
import com.openevent.identity.user.User;

import lombok.RequiredArgsConstructor;

/**
 * Refresh token là chuỗi ngẫu nhiên; database chỉ lưu SHA-256. Mỗi lần refresh sinh token mới và thu hồi token
 * cũ. Token đã thu hồi mà bị dùng lại được coi là bị đánh cắp, nên mọi phiên của người dùng đó bị thu hồi.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final RefreshTokenRepository tokens;
    private final JwtProperties properties;

    /** Tham gia transaction của nơi gọi. Trả về token gốc, chỉ xuất hiện trong response. */
    @Transactional(propagation = Propagation.MANDATORY)
    String issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new RefreshToken(user, hash(raw), Instant.now().plus(properties.refreshTokenTtl())));
        return raw;
    }

    /**
     * Thu hồi token và trả về chủ sở hữu. Khi token bị dùng lại, việc thu hồi mọi phiên phải được commit dù
     * request thất bại, nên {@link ApiException} không làm rollback transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ApiException.class)
    User consume(String raw) {
        RefreshToken token = tokens.findByTokenHash(hash(raw)).orElseThrow(AuthErrors::invalidRefreshToken);
        User user = token.getUser();
        UUID userId = user.getId();
        if (token.isRevoked() || tokens.revokeIfActive(token.getId()) == 0) {
            log.warn("Refresh token đã thu hồi bị dùng lại, thu hồi mọi phiên của user {}", userId);
            tokens.revokeAllByUserId(userId);
            throw AuthErrors.invalidRefreshToken();
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw AuthErrors.invalidRefreshToken();
        }
        return user;
    }

    @Transactional
    public void revoke(String raw) {
        tokens.findByTokenHash(hash(raw)).ifPresent(token -> tokens.revokeIfActive(token.getId()));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        tokens.revokeAllByUserId(userId);
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteExpired() {
        int deleted = tokens.deleteExpired(Instant.now());
        log.info("Đã xóa {} refresh token hết hạn", deleted);
    }

    /** SHA-256 hex; quên mật khẩu dùng chung. */
    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
