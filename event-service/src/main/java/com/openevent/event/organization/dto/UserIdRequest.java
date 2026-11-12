package com.openevent.event.organization.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Chuyển quyền sở hữu, đổi HOST, thêm STAFF: chọn từ danh sách thành viên nên đã có user id. */
public record UserIdRequest(@NotNull UUID userId) {
}
