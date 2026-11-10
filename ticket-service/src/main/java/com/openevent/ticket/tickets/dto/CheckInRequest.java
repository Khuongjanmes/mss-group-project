package com.openevent.ticket.tickets.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mã trong QR, quét hoặc gõ tay. */
public record CheckInRequest(@NotBlank @Size(max = 64) String code) {
}
