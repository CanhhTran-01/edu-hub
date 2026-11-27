package com.eduhub.edu_hub.academic.api.dto.response;

import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

/** Snapshot đọc tạm thời cho popup; snapshotRef nhận diện dữ liệu, không phải ID bản ghi DB. */
@Builder
public record GpaSimulationSnapshotResponse(
        String snapshotRef,
        LocalDateTime observedAt,
        SemesterResponse semester,
        boolean historyComplete,
        String scopeNotice,
        Integer requiredGraduationCredits,
        BigDecimal graduationTargetGpa,
        BigDecimal semesterTargetGpa,
        BigDecimal defaultGrade4,
        List<GradeOptionResponse> gradeOptions,
        List<CourseResponse> courses) {
    public record GradeOptionResponse(BigDecimal grade4, List<String> letters) {}

    public record CourseResponse(
            Long enrollmentId,
            Long subjectId,
            String subjectCode,
            String subjectName,
            int credits,
            boolean canAdjustGrade,
            BigDecimal actualGrade4,
            String actualGradeLetter) {}
}
