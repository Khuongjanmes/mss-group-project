package com.openevent.identity.auth;

import org.springframework.http.HttpStatus;

import com.openevent.common.web.ApiException;

public final class AuthErrors {

    private AuthErrors() {
    }

    public static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng");
    }

    public static ApiException accountLocked() {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "Tài khoản đã bị khóa");
    }

    public static ApiException invalidRefreshToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                "Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
    }

    public static ApiException invalidGoogleToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN", "Token Google không hợp lệ");
    }
}
