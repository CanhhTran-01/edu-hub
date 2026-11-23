package com.eduhub.edu_hub.enrollment.api.dto.response;

import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Nguồn đọc học vụ và lịch cá nhân; các lượt hủy/ẩn không nằm trong danh sách hiển thị. */
public record StudentLearningHistoryResponse(
        boolean historyComplete,
        int firstSupportedAdmissionYear,
        String scopeNotice,
        Set<Long> passedSubjectIds,
        List<LearningRecordResponse> enrollments) {
    public record LearningRecordResponse(
            Long enrollmentId,
            Long moduleClassId,
            String moduleClassCode,
            Long subjectId,
            String subjectCode,
            String subjectName,
            Long semesterId,
            int academicYearStart,
            int semesterNumber,
            int credits,
            LocalDateTime registeredAt,
            EnrollmentStatus status,
            BigDecimal componentScore1,
            BigDecimal componentScore2,
            BigDecimal processScore,
            BigDecimal finalExamScore,
            BigDecimal courseScore10,
            String courseScoreLetter,
            BigDecimal courseScore4,
            LocalDateTime gradePublishedAt,
            LocalDateTime gradeLockAt,
            boolean gradeLocked) {}
}
