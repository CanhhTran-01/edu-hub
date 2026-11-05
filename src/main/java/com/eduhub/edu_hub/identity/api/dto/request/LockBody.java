package com.eduhub.edu_hub.identity.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LockBody(@NotBlank String reason) {}
