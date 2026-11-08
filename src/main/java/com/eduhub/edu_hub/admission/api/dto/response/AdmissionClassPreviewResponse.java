package com.eduhub.edu_hub.admission.api.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;

@Builder
public record AdmissionClassPreviewResponse(
        Long id,
        String className,
        int targetSize,
        int assignedSize,
        int currentSize,
        int minimum,
        int maximum,
        int reserve,
        int reserveUsed,
        BigDecimal averageScore,
        List<Long> applicantIds) {}
