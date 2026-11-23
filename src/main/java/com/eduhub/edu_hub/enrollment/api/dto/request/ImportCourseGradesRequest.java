package com.eduhub.edu_hub.enrollment.api.dto.request;

import org.springframework.web.multipart.MultipartFile;

public record ImportCourseGradesRequest(MultipartFile file) {}
