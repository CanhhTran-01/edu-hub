package com.eduhub.edu_hub.enrollment.api.dto.response;

import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Một dòng là một lượt học, không gộp các lần học lại của cùng sinh viên và học phần. */
public record CourseGradeResponse(
        Long enrollmentId,
        Long studentId,
        String studentCode,
        String fullName,
        Long moduleClassId,
        String moduleClassCode,
        Long semesterId,
        Long subjectId,
        String subjectCode,
        String subjectName,
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
