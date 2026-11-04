package com.eduhub.edu_hub.people.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdmissionClassPreview(
        Long classId,
        String className,
        int currentSize,
        int projectedSize,
        int minCapacity,
        int maxCapacity,
        BigDecimal projectedAverageEntranceScore,
        List<AdmissionStudentPreview> students) {}
