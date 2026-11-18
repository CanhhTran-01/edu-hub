package com.eduhub.edu_hub.academic.api.dto.response;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.StudentLearningHistoryResponse.LearningRecordResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import java.math.BigDecimal;
import java.util.List;

public record StudentTranscriptResponse(
        boolean historyComplete, String scopeNotice, List<SemesterTranscriptResponse> semesters) {

    public record SemesterTranscriptResponse(
            SemesterResponse semester,
            BigDecimal semesterGpa,
            BigDecimal cumulativeGpa,
            Integer accumulatedCredits,
            int registeredCredits,
            int gradedCourseCount,
            List<AcademicCourseResponse> courses) {}

    public record AcademicCourseResponse(
            LearningRecordResponse enrollment, Boolean prerequisitesMet, List<PrerequisiteResponse> prerequisites) {}

    /** passed=null nghĩa là chưa đủ lịch sử để kết luận chưa đạt. */
    public record PrerequisiteResponse(SubjectSimpleResponse subject, Boolean passed) {}
}
