package com.openevent.ticket.tickettypes.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code eventId} chỉ dùng khi tạo; khi sửa, sự kiện lấy từ loại vé trong DB (KB-3 bước 4). */
public record TicketTypeRequest(
        UUID eventId,
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin("0") @DecimalMax("1000000000") @Digits(integer = 12, fraction = 0) BigDecimal price,
        @NotNull @Min(1) @Max(100000) Integer totalQuantity,
        @NotNull Instant saleStart,
        @NotNull Instant saleEnd) {
}
