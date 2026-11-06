package com.openevent.common.web;

import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ, được {@link GlobalExceptionHandler} chuyển thành {@link ApiError}. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
