package com.openevent.event.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest(@NotBlank @Size(max = 500) String reason) {
}
