package com.eduhub.edu_hub.people.api.dto.response;

import java.util.List;
import java.util.UUID;

public record AdmissionConfirmResponse(
        UUID batchId, int createdStudents, int createdUsers, List<String> emailFailures) {}
