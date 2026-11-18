package com.eduhub.edu_hub.academic.api.dto.response;

import com.eduhub.edu_hub.academic.api.dto.response.StudentTranscriptResponse.AcademicCourseResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import java.util.List;

public record StudentSemesterRegistrationResponse(
        SemesterResponse semester,
        int registeredCredits,
        boolean historyComplete,
        String scopeNotice,
        List<AcademicCourseResponse> courses) {}
