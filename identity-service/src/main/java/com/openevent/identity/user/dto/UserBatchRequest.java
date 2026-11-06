package com.openevent.identity.user.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserBatchRequest(@NotEmpty @Size(max = 500) List<@NotNull UUID> ids) {
}
