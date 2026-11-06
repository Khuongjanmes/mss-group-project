package com.openevent.identity.auth.google;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.openevent.common.web.ApiException;

@Configuration
class GoogleConfig {

    @Bean
    GoogleIdTokenVerifier googleIdTokenVerifier(GoogleProperties properties) {
        if (!StringUtils.hasText(properties.clientId())) {
            return idToken -> {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_LOGIN_DISABLED",
                        "Đăng nhập bằng Google chưa được cấu hình");
            };
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        return new NimbusGoogleIdTokenVerifier(properties.clientId(), NimbusJwtDecoder
                .withJwkSetUri(properties.jwkSetUri())
                .restOperations(new RestTemplate(requestFactory))
                .build());
    }
}
