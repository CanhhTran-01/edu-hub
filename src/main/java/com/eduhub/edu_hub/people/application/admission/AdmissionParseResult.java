package com.eduhub.edu_hub.people.application.admission;

import com.eduhub.edu_hub.people.api.dto.AdmissionErrorResponse;
import java.util.List;

public record AdmissionParseResult(
        int totalRows, List<AdmissionCandidate> candidates, List<AdmissionErrorResponse> errors) {}
