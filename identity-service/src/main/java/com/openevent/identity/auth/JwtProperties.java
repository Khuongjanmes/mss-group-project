package com.openevent.identity.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Secret phải giống API Gateway. Thiếu hoặc quá ngắn thì service không khởi động. */
@ConfigurationProperties("openevent.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("openevent.jwt.secret phải có ít nhất " + MIN_SECRET_BYTES + " byte");
        }
    }
}
