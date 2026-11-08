package com.eduhub.edu_hub.admission.api.dto.request;

import lombok.Builder;
import org.springframework.web.multipart.MultipartFile;

@Builder
public record ImportAdmissionApplicantsRequest(long expectedRevision, MultipartFile file) {}
