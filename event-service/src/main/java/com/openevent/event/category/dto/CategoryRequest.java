package com.openevent.event.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^\\P{Cntrl}*$", message = "không được chứa ký tự điều khiển")
        String name,
        @Size(max = 1000)
        String description) {
}
