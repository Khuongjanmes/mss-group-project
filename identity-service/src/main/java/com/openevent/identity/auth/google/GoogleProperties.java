package com.openevent.identity.auth.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("openevent.google")
public record GoogleProperties(String clientId, String jwkSetUri) {
}
