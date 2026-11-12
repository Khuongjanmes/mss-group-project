package com.openevent.ticket.tickets.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mã trong QR, quét hoặc gõ tay; {@code eventId} là sự kiện đang soát (không bắt buộc), vé sự kiện khác bị từ chối. */
public record CheckInRequest(@NotBlank @Size(max = 64) String code, UUID eventId) {
}
