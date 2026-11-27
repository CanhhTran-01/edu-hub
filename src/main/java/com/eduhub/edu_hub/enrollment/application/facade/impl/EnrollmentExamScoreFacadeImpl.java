package com.eduhub.edu_hub.enrollment.application.facade.impl;

import com.eduhub.edu_hub.enrollment.api.dto.request.MinimumExamScoreRequest;
import com.eduhub.edu_hub.enrollment.api.dto.response.MinimumExamScoreResponse;
import com.eduhub.edu_hub.enrollment.application.facade.EnrollmentFacade;
import com.eduhub.edu_hub.enrollment.application.service.MinimumExamScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EnrollmentExamScoreFacadeImpl implements EnrollmentFacade.ExamScore {
    private final MinimumExamScoreService examScoreService;

    @Override
    public MinimumExamScoreResponse solveMinimumExamScore(MinimumExamScoreRequest request) {
        return examScoreService.solveMinimumExamScore(request);
    }
}
