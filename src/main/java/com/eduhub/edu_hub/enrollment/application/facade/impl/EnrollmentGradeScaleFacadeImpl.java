package com.eduhub.edu_hub.enrollment.application.facade.impl;

import com.eduhub.edu_hub.enrollment.api.dto.response.CourseGradeScaleResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.CourseGradeScaleResponse.GradeBandResponse;
import com.eduhub.edu_hub.enrollment.application.facade.EnrollmentFacade;
import com.eduhub.edu_hub.enrollment.domain.policy.CourseGradePolicy;
import com.eduhub.edu_hub.enrollment.infrastructure.config.CourseGradeSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Consumer chỉ nhận thang đã kiểm tra qua facade, không phụ thuộc settings hoặc policy nội bộ Enrollment. */
@Service
@RequiredArgsConstructor
public class EnrollmentGradeScaleFacadeImpl implements EnrollmentFacade.GradeScale {
    private final CourseGradeSettings settings;

    @Override
    public CourseGradeScaleResponse findCurrentGradeScale() {
        return new CourseGradeScaleResponse(CourseGradePolicy.requireValidScale(settings.getGradeScale()).stream()
                .map(band -> new GradeBandResponse(band.minimumScore(), band.letter(), band.gradePoint()))
                .toList());
    }
}
