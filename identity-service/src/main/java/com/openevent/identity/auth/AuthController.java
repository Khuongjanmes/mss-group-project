package com.openevent.identity.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.openevent.identity.auth.dto.AuthResponse;
import com.openevent.identity.auth.dto.GoogleClientConfig;
import com.openevent.identity.auth.dto.GoogleLoginRequest;
import com.openevent.identity.auth.dto.LoginRequest;
import com.openevent.identity.auth.dto.RefreshRequest;
import com.openevent.identity.auth.dto.RegisterRequest;
import com.openevent.identity.auth.google.GoogleProperties;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokens;
    private final GoogleProperties google;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** SPA đọc lúc chạy để vẽ nút Google, nên chỉ cần cấu hình GOOGLE_CLIENT_ID một chỗ. */
    @GetMapping("/google/config")
    public GoogleClientConfig googleConfig() {
        return new GoogleClientConfig(google.clientId() == null ? "" : google.clientId());
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.loginWithGoogle(request.idToken());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokens.revoke(request.refreshToken());
    }
}
