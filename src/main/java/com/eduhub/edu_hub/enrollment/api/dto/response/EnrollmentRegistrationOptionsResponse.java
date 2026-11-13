package com.eduhub.edu_hub.enrollment.api.dto.response;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassResponse;
import java.util.List;

public record EnrollmentRegistrationOptionsResponse(
        Long semesterId,
        boolean registrationAllowed,
        int registeredCredits,
        int minimumCredits,
        int maximumCredits,
        List<String> warnings,
        EnrollmentScopeResponse scope,
        List<RegistrationClassResponse> moduleClasses,
        List<EnrollmentResponse> enrollments) {

    public record RegistrationClassResponse(
            ModuleClassResponse moduleClass,
            boolean prerequisitesMet,
            List<SubjectSimpleResponse> missingPrerequisites) {}
}
