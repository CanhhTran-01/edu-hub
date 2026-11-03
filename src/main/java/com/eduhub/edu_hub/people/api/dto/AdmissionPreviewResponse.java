package com.eduhub.edu_hub.people.api.dto;

import java.util.List;
import java.util.UUID;

public record AdmissionPreviewResponse(
        UUID batchId,
        String fileName,
        int totalRows,
        int validRows,
        int invalidRows,
        List<AdmissionClassPreview> classes,
        List<AdmissionErrorResponse> errors,
        List<String> duplicateNameWarnings,
        List<String> capacityWarnings) {}
