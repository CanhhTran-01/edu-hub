package com.eduhub.edu_hub.enrollment.api.dto.response;

import java.util.List;

public record EnrollmentRegistrationResponse(
        EnrollmentResponse enrollment,
        int registeredCredits,
        int minimumCredits,
        int maximumCredits,
        List<String> warnings,
        EnrollmentScopeResponse scope) {}
