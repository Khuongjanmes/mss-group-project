package com.openevent.ticket.tickets.dto;

import java.time.Instant;
import java.util.UUID;

import com.openevent.ticket.tickets.Ticket;

/** Danh sách người tham dự cho người quản lý sự kiện; họ tên và email lấy từ Identity. */
public record AttendeeResponse(UUID ticketId, String code, Ticket.Status status, Instant checkedInAt,
        String ticketTypeName, UUID ownerId, String ownerName, String ownerEmail) {
}
