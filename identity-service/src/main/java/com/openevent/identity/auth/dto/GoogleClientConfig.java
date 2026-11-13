package com.openevent.identity.auth.dto;

/** Client ID của Google cho nút đăng nhập trên SPA; rỗng khi chưa cấu hình (SPA ẩn nút). Client ID là công khai. */
public record GoogleClientConfig(String clientId) {
}
