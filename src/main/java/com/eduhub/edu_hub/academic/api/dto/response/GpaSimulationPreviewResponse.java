package com.eduhub.edu_hub.academic.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

/** Kết quả đã tính trên BE, phân biệt điểm thật với giả định và giới hạn lịch sử tích lũy. */
@Builder
public record GpaSimulationPreviewResponse(
        String snapshotRef,
        Long semesterId,
        LocalDateTime calculatedAt,
        BigDecimal semesterGpa,
        BigDecimal cumulativeGpa,
        Integer accumulatedCredits,
        int semesterCredits,
        BigDecimal semesterTargetGpa,
        Boolean meetsSemesterTarget,
        BigDecimal semesterTargetGap,
        BigDecimal graduationTargetGpa,
        Integer requiredGraduationCredits,
        boolean historyComplete,
        String scopeNotice,
        List<CourseResponse> courses) {
    public record CourseResponse(
            Long enrollmentId,
            String subjectCode,
            String subjectName,
            int credits,
            boolean hasPublishedGrade,
            BigDecimal grade4,
            List<String> gradeLetters) {}
}
