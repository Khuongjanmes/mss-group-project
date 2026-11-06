package com.openevent.common.security;

import java.io.IOException;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.openevent.common.web.ApiError;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cấu hình bảo mật mặc định cho service servlet: stateless, lấy danh tính từ Gateway, mặc định yêu cầu
 * đăng nhập. Phân quyền chi tiết dùng {@code @PreAuthorize("hasRole('ADMIN')")} trên controller.
 *
 * <p>{@code /internal/**} dành cho Feign: Gateway chặn đường này từ bên ngoài.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"})
@EnableMethodSecurity
@EnableConfigurationProperties(OpenEventSecurityProperties.class)
public class ServiceSecurityAutoConfiguration {

    private static final String[] ALWAYS_PERMITTED = {
            "/internal/**", "/actuator/health/**", "/actuator/info", "/error",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

    @Bean
    @ConditionalOnMissingBean
    SecurityFilterChain serviceSecurityFilterChain(HttpSecurity http, OpenEventSecurityProperties properties,
            JsonMapper jsonMapper) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new GatewayHeaderAuthenticationFilter(), AnonymousAuthenticationFilter.class)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> writeError(jsonMapper, request, response,
                                HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Bạn chưa đăng nhập"))
                        .accessDeniedHandler((request, response, ex) -> writeError(jsonMapper, request, response,
                                HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này")))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(ALWAYS_PERMITTED).permitAll();
                    for (String path : properties.publicPaths()) {
                        auth.requestMatchers(toMatcher(path)).permitAll();
                    }
                    auth.anyRequest().authenticated();
                });
        return http.build();
    }

    /** Service không xác thực bằng mật khẩu; bean này ngăn Boot tạo user mặc định với mật khẩu ngẫu nhiên. */
    @Bean
    @ConditionalOnMissingBean
    AuthenticationManager gatewayOnlyAuthenticationManager() {
        return authentication -> {
            throw new BadCredentialsException("Service chỉ nhận danh tính từ Gateway");
        };
    }

    private static PathPatternRequestMatcher toMatcher(String definition) {
        String[] parts = definition.trim().split("\\s+");
        if (parts.length == 2) {
            return PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.valueOf(parts[0]), parts[1]);
        }
        if (parts.length == 1) {
            return PathPatternRequestMatcher.withDefaults().matcher(parts[0]);
        }
        throw new IllegalArgumentException("openevent.security.public-paths không hợp lệ: " + definition);
    }

    private static void writeError(JsonMapper jsonMapper, HttpServletRequest request, HttpServletResponse response,
            HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getWriter(), ApiError.of(status.value(), code, message, request.getRequestURI()));
    }
}
