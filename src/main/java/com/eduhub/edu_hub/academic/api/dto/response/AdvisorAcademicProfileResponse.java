package com.eduhub.edu_hub.academic.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Số liệu học vụ tối thiểu cho Advisor; không mang danh tính, liên hệ hoặc giấy tờ sang model. */
public record AdvisorAcademicProfileResponse(
        Long currentSemesterId,
        String currentSemesterName,
        BigDecimal cumulativeGpa,
        BigDecimal currentSemesterGpa,
        Integer accumulatedCredits,
        Integer requiredGraduationCredits,
        Integer currentRegisteredCredits,
        BigDecimal graduationTargetGpa,
        int currentSemesterCourseCount,
        int currentSemesterGradedCourseCount,
        boolean historyComplete,
        String scopeNotice,
        LocalDateTime observedAt) {}
