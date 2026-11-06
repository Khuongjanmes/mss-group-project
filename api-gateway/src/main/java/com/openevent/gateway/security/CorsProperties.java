package com.openevent.gateway.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("openevent.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
