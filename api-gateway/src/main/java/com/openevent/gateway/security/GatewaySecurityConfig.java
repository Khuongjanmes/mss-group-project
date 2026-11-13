package com.openevent.gateway.security;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import com.openevent.gateway.web.GatewayErrorWriter;

import reactor.core.publisher.Mono;

/**
 * Lớp phân quyền thô tại Gateway. Quyền theo dữ liệu (ví dụ "chỉ organizer của sự kiện") do từng service kiểm tra.
 * Khi thêm API công khai hoặc API theo vai trò mới, cập nhật danh sách ở {@link #securityFilterChain}.
 */
@Configuration
class GatewaySecurityConfig {

    private static final String ADMIN = "ADMIN";

    /** Đăng nhập, và webhook payOS (payment-service tự kiểm tra chữ ký). */
    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/api/auth/register", "/api/auth/login", "/api/auth/google", "/api/auth/refresh", "/api/auth/logout",
            "/api/auth/forgot-password", "/api/auth/reset-password",
            "/api/payments/payos-webhook"};

    private static final String[] PUBLIC_READ_ENDPOINTS = {
            "/api/events/**", "/api/categories/**", "/api/venues/**", "/api/ticket-types/**",
            "/api/payments/vnpay-return", "/api/auth/google/config"};

    @Bean
    SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder decoder,
            CorsConfigurationSource corsSource, GatewayErrorWriter errors) {
        ServerAuthenticationEntryPoint unauthorized = (exchange, ex) -> {
            exchange.getResponse().getHeaders().set("WWW-Authenticate", "Bearer");
            return errors.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Bạn chưa đăng nhập");
        };
        ServerAccessDeniedHandler forbidden = (exchange, ex) -> errors.write(exchange, HttpStatus.FORBIDDEN,
                "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này");

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .cors(cors -> cors.configurationSource(corsSource))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .oauth2ResourceServer(server -> server
                        .bearerTokenConverter(bearerTokenConverter())
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden)
                        .jwt(jwt -> jwt
                                .jwtDecoder(decoder)
                                .jwtAuthenticationConverter(authenticationConverter())))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .pathMatchers("/internal/**", "/actuator", "/actuator/**").denyAll()

                        .pathMatchers(HttpMethod.POST, PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .pathMatchers(HttpMethod.GET, PUBLIC_READ_ENDPOINTS).permitAll()

                        .pathMatchers("/api/users/me", "/api/users/me/**").authenticated()
                        .pathMatchers("/api/users/**").hasRole(ADMIN)

                        .pathMatchers(HttpMethod.PUT, "/api/events/*/approve", "/api/events/*/reject").hasRole(ADMIN)
                        .pathMatchers("/api/categories/**", "/api/venues/**").hasRole(ADMIN)
                        // Sự kiện, tổ chức, loại vé, check-in: ai đăng nhập cũng gọi được;
                        // service tự kiểm tra quyền theo tổ chức và sự kiện (BR-14).

                        .pathMatchers(HttpMethod.GET, "/api/payments").hasRole(ADMIN)
                        .pathMatchers(HttpMethod.PUT, "/api/payments/*/refunded").hasRole(ADMIN)

                        .anyExchange().authenticated())
                .build();
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(GatewayJwtProperties properties) {
        SecretKey key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), requiredClaims()));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Bỏ qua token trên /api/auth/**: SPA có thể gửi kèm access token đã hết hạn khi gọi refresh hoặc login, và
     * các API này không cần danh tính.
     */
    private static ServerAuthenticationConverter bearerTokenConverter() {
        ServerBearerTokenAuthenticationConverter delegate = new ServerBearerTokenAuthenticationConverter();
        ServerWebExchangeMatcher authEndpoints = ServerWebExchangeMatchers.pathMatchers(PUBLIC_AUTH_ENDPOINTS);
        return exchange -> authEndpoints.matches(exchange)
                .flatMap(match -> match.isMatch() ? Mono.empty() : delegate.convert(exchange));
    }

    private static ReactiveJwtAuthenticationConverterAdapter authenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    /** Validator mặc định chỉ kiểm tra exp khi có; ở đây bắt buộc có exp và subject là UUID của người dùng. */
    private static OAuth2TokenValidator<Jwt> requiredClaims() {
        OAuth2Error invalid = new OAuth2Error("invalid_token", "Thiếu claim bắt buộc", null);
        return jwt -> jwt.getExpiresAt() != null && isUuid(jwt.getSubject())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(invalid);
    }

    private static boolean isUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
