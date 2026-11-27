package com.eduhub.edu_hub.enrollment.api.dto.response;

import com.eduhub.edu_hub.enrollment.domain.enums.MinimumExamScoreStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

/** Giữ đầu vào, thang và kết quả đã xác minh để mở lại hoặc tiếp tục tính trong hội thoại. */
@Builder
public record MinimumExamScoreResponse(
        MinimumExamScoreStatus status,
        String subjectName,
        BigDecimal componentScore1,
        BigDecimal componentScore2,
        BigDecimal processScore,
        String targetGradeLetter,
        BigDecimal targetMinimumScore10,
        BigDecimal minimumExamScore,
        BigDecimal minimumExamScoreTwoDecimals,
        BigDecimal achievedCourseScore10,
        String achievedGradeLetter,
        BigDecimal maximumCourseScore10,
        String maximumGradeLetter,
        CourseGradeScaleResponse gradeScale,
        LocalDateTime calculatedAt,
        String message) {}
