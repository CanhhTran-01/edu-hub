package com.eduhub.edu_hub.academic.application.facade.dto;

import com.eduhub.edu_hub.enrollment.api.dto.response.EnrollmentResponse;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SemesterGradeResponse {
    private String semesterCode;
    private BigDecimal semesterGpa;
    private int credits;
    private List<EnrollmentResponse> courses;
}
