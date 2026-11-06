package com.openevent.common.security;

/** Header Gateway thêm sau khi xác minh JWT. Gateway luôn xóa các header này nếu client tự gửi. */
public final class GatewayHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_EMAIL = "X-User-Email";
    /** Danh sách vai trò, cách nhau bằng dấu phẩy, ví dụ {@code ATTENDEE,ORGANIZER}. */
    public static final String USER_ROLES = "X-User-Roles";

    private GatewayHeaders() {
    }
}
