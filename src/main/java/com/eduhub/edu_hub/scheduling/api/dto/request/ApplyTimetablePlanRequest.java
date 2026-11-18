package com.eduhub.edu_hub.scheduling.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ApplyTimetablePlanRequest(@NotNull @PositiveOrZero Long expectedRevision) {}
