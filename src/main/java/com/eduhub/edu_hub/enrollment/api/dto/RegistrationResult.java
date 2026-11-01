package com.eduhub.edu_hub.enrollment.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegistrationResult {
    private boolean success;
    private String message;
    private EnrollmentResponse enrollment;
}
