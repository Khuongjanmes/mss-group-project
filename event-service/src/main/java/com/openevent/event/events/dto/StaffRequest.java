package com.openevent.event.events.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Thêm STAFF bằng email tài khoản OpenEvent; STAFF không cần thuộc tổ chức. */
public record StaffRequest(@NotBlank @Email @Size(max = 254) String email) {
}
