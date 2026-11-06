package com.openevent.gateway.security;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Service phía sau tin các header X-User-*. Vì vậy filter này luôn xóa mọi header X-User-* do client gửi lên,
 * rồi chỉ thêm lại từ JWT đã xác minh. Authorization không được chuyển tiếp để token không lan sang các service.
 */
@Component
class IdentityHeadersFilter implements GlobalFilter, Ordered {

    private static final String IDENTITY_HEADER_PREFIX = "x-user-";
    private static final String USER_ID = "X-User-Id";
    private static final String USER_EMAIL = "X-User-Email";
    private static final String USER_ROLES = "X-User-Roles";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .map(token -> Optional.of(token.getToken()))
                .defaultIfEmpty(Optional.empty())
                .flatMap(jwt -> {
                    ServerHttpRequest request = exchange.getRequest().mutate()
                            .headers(headers -> rewrite(headers, jwt))
                            .build();
                    return chain.filter(exchange.mutate().request(request).build());
                });
    }

    private static void rewrite(HttpHeaders headers, Optional<Jwt> jwt) {
        headers.headerNames().stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(IDENTITY_HEADER_PREFIX))
                .toList()
                .forEach(headers::remove);
        headers.remove(HttpHeaders.AUTHORIZATION);
        jwt.ifPresent(token -> {
            headers.set(USER_ID, token.getSubject());
            String email = token.getClaimAsString("email");
            if (email != null) {
                headers.set(USER_EMAIL, email);
            }
            List<String> roles = token.getClaimAsStringList("roles");
            if (roles != null && !roles.isEmpty()) {
                headers.set(USER_ROLES, String.join(",", roles));
            }
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
