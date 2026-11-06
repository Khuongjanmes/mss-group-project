package com.openevent.gateway.security;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Secret phải giống Identity Service. Thiếu hoặc quá ngắn thì Gateway không khởi động. */
@ConfigurationProperties("openevent.jwt")
public record GatewayJwtProperties(String secret, String issuer) {

    private static final int MIN_SECRET_BYTES = 32;

    public GatewayJwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("openevent.jwt.secret phải có ít nhất " + MIN_SECRET_BYTES + " byte");
        }
    }
}
