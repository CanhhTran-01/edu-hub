package com.eduhub.edu_hub.admission.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Tham số query khi bỏ hồ sơ lỗi; DELETE không nhận body và luôn yêu cầu revision hiện hành. */
public record DeleteAdmissionApplicantRequest(@NotNull @Min(0) Long expectedRevision) {}
