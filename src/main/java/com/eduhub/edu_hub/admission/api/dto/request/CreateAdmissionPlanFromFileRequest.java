package com.eduhub.edu_hub.admission.api.dto.request;

import lombok.Builder;
import org.springframework.web.multipart.MultipartFile;

@Builder
public record CreateAdmissionPlanFromFileRequest(
        Long majorId,
        Long cohortId,
        Integer minCapacity,
        Integer maxCapacity,
        Integer reserveCapacity,
        MultipartFile file) {}
