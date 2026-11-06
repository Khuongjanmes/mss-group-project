package com.openevent.gateway.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GatewayJwtPropertiesTest {

    @Test
    void rejectsMissingOrShortSecret() {
        assertThatThrownBy(() -> new GatewayJwtProperties(null, "i")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GatewayJwtProperties("short", "i")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsSecretOf32Bytes() {
        assertThatCode(() -> new GatewayJwtProperties("x".repeat(32), "i")).doesNotThrowAnyException();
    }
}
