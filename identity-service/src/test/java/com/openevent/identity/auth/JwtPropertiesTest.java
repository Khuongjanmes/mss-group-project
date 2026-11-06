package com.openevent.identity.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    private static JwtProperties withSecret(String secret) {
        return new JwtProperties(secret, "issuer", Duration.ofMinutes(15), Duration.ofDays(7));
    }

    @Test
    void rejectsSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> withSecret("too-short")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> withSecret(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsSecretOf32Bytes() {
        assertThatCode(() -> withSecret("x".repeat(32))).doesNotThrowAnyException();
    }
}
