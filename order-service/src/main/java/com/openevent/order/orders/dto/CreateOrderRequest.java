package com.openevent.order.orders.dto;

import java.util.List;
import java.util.UUID;

import com.openevent.order.orders.Quotas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Đặt vé: chọn sự kiện và số lượng từng loại vé. Giá không nhận từ client (KB-1 bước 5). */
public record CreateOrderRequest(
        @NotNull UUID eventId,
        @NotEmpty @Size(max = Quotas.MAX_PER_EVENT) List<@Valid @NotNull Item> items) {

    public record Item(@NotNull UUID ticketTypeId, @Min(1) @Max(Quotas.MAX_PER_EVENT) int quantity) {
    }

    public int ticketCount() {
        return items.stream().mapToInt(Item::quantity).sum();
    }
}
