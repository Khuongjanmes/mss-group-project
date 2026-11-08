package com.openevent.event.events.dto;

import java.util.UUID;

/** {@code email}, {@code fullName}: {@code null} khi Identity không phản hồi. */
public record StaffResponse(UUID userId, String email, String fullName) {
}
