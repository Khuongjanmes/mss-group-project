package com.openevent.event.events.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EventRequest(
        @NotBlank @Size(max = 200) @Pattern(regexp = "^\\P{Cntrl}*$", message = "không được chứa ký tự điều khiển")
        String title,
        @Size(max = 20000)
        String description,
        @Size(max = 500) @Pattern(regexp = "(?i)^https?://\\S+$", message = "phải là URL http hoặc https")
        String bannerUrl,
        @NotNull UUID categoryId,
        @NotNull UUID venueId,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        /** Không bắt buộc; chỉ dùng lúc tạo, bỏ qua khi sửa (BR-14). */
        UUID organizationId) {
}
