package com.openevent.identity.user.dto;

import java.util.UUID;

import com.openevent.identity.user.User;
import com.openevent.identity.user.UserStatus;

/** Thông tin tối thiểu cho service khác: gửi email, danh sách người tham dự, kiểm tra khóa (BR-12). */
public record UserContactResponse(UUID id, String email, String fullName, String phone, UserStatus status) {

    public static UserContactResponse from(User user) {
        return new UserContactResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getStatus());
    }
}
