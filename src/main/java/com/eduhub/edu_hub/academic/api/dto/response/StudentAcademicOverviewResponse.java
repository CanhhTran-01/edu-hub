package com.eduhub.edu_hub.academic.api.dto.response;

import com.eduhub.edu_hub.people.api.dto.response.StudentAcademicProfileResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import java.math.BigDecimal;
import java.util.List;

public record StudentAcademicOverviewResponse(
        StudentAcademicProfileResponse student,
        SemesterResponse currentSemester,
        List<SemesterResponse> semesters,
        BigDecimal cumulativeGpa,
        BigDecimal currentSemesterGpa,
        Integer accumulatedCredits,
        Integer requiredGraduationCredits,
        Integer currentRegisteredCredits,
        int currentSemesterCourseCount,
        int currentSemesterGradedCourseCount,
        boolean historyComplete,
        int firstSupportedAdmissionYear,
        String scopeNotice) {}
