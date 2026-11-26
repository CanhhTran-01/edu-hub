package com.eduhub.edu_hub.academic.application.facade.impl;

import com.eduhub.edu_hub.academic.api.dto.response.AdvisorAcademicProfileResponse;
import com.eduhub.edu_hub.academic.application.facade.AcademicFacade;
import com.eduhub.edu_hub.academic.application.service.StudentAcademicQueryService;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** Dùng lại snapshot đọc và quy tắc GPA của Academic, chỉ chiếu các trường Advisor cần. */
@Service
@RequiredArgsConstructor
public class AcademicAdvisorFacadeImpl implements AcademicFacade.Advisor {
    private final StudentAcademicQueryService academicQueryService;
    private final Clock clock;

    @Override
    public AdvisorAcademicProfileResponse findMyAcademicProfile(Jwt principal) {
        var overview = academicQueryService.findMyAcademicOverview(principal);
        var semester = overview.currentSemester();
        return new AdvisorAcademicProfileResponse(
                semester == null ? null : semester.getId(),
                semester == null ? null : semester.getSemesterName(),
                overview.cumulativeGpa(),
                overview.currentSemesterGpa(),
                overview.accumulatedCredits(),
                overview.requiredGraduationCredits(),
                overview.currentRegisteredCredits(),
                overview.student().targetGpa(),
                overview.currentSemesterCourseCount(),
                overview.currentSemesterGradedCourseCount(),
                overview.historyComplete(),
                overview.scopeNotice(),
                LocalDateTime.now(clock));
    }
}
