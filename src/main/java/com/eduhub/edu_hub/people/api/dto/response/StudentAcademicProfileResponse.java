package com.eduhub.edu_hub.people.api.dto.response;

import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.people.domain.enums.StudentActivityStatus;
import java.math.BigDecimal;

/** Hồ sơ tối thiểu để đọc học vụ; không chứa thông tin liên hệ hoặc giấy tờ định danh. */
public record StudentAcademicProfileResponse(
        Long studentId,
        String studentCode,
        String fullName,
        Long majorId,
        String majorName,
        Long cohortId,
        String cohortCode,
        String className,
        TrainingType trainingType,
        Integer admissionYear,
        StudentActivityStatus activityStatus,
        BigDecimal targetGpa,
        BigDecimal semesterTargetGpa,
        String academicWarningStatus,
        String academicWarningReason) {}
