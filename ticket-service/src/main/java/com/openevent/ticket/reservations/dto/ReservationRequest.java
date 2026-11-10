package com.openevent.ticket.reservations.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Khớp {@code TicketClient.ReservationRequest} của Order. Giới hạn 5 vé mỗi sự kiện (BR-03) do Order kiểm tra. */
public record ReservationRequest(@NotNull UUID orderId, @NotEmpty @Size(max = 10) List<@Valid @NotNull Item> items) {

    public record Item(@NotNull UUID ticketTypeId, @Min(1) @Max(10) int quantity) {
    }
}
