package com.openevent.identity.auth.google;

/** Thông tin đã xác minh từ ID token của Google. */
public record GoogleIdentity(String subject, String email, String name, String picture, boolean emailVerified) {
}
