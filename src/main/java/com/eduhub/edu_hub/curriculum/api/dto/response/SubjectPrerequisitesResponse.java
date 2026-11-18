package com.eduhub.edu_hub.curriculum.api.dto.response;

import java.util.List;

public record SubjectPrerequisitesResponse(Long subjectId, List<SubjectSimpleResponse> prerequisites) {}
