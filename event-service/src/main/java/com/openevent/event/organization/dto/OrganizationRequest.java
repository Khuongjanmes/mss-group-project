package com.openevent.event.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrganizationRequest(
        @NotBlank @Size(max = 200) @Pattern(regexp = "^\\P{Cntrl}*$", message = "không được chứa ký tự điều khiển")
        String name,
        @Size(max = 2000)
        String description) {
}
