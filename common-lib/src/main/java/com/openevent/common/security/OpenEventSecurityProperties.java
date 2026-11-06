package com.openevent.common.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param publicPaths các API không cần đăng nhập, dạng {@code "GET /api/events/**"} hoặc {@code "/api/x/**"}.
 */
@ConfigurationProperties("openevent.security")
public record OpenEventSecurityProperties(List<String> publicPaths) {

    public OpenEventSecurityProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }
}
