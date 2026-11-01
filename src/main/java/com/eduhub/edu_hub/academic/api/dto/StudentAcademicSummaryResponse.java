package com.eduhub.edu_hub.academic.api.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudentAcademicSummaryResponse {
    private String studentCode;
    private String fullName;
    private String className;
    private String majorName;
    private String cohortCode;
    private BigDecimal cumulativeGpa;
    private int accumulatedCredits;
    private int totalRequiredCredits;
    private BigDecimal targetGpa;
    private String academicWarningStatus;
    private List<SemesterGradeResponse> semesterGrades;
}
