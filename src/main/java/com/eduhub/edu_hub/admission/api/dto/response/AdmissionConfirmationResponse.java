package com.eduhub.edu_hub.admission.api.dto.response;

import java.util.List;
import lombok.Builder;

@Builder
public record AdmissionConfirmationResponse(
        Long planId,
        long revision,
        String state,
        int enrolled,
        int failed,
        int skipped,
        List<AdmissionApplicantResponse> applicants) {}
