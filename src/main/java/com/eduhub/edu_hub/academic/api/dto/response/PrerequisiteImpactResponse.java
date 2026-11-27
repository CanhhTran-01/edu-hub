package com.eduhub.edu_hub.academic.api.dto.response;

import com.eduhub.edu_hub.academic.domain.enums.*;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

/** Phân tích từng giả định trượt độc lập, không phải yêu cầu sửa kịch bản GPA hoặc điểm thật. */
@Builder
public record PrerequisiteImpactResponse(
        PrerequisiteAnalysisStatus status,
        PrerequisiteAnalysisScope scope,
        SemesterResponse semester,
        boolean historyComplete,
        boolean graphComplete,
        AcademicRequirementStatus prerequisiteStatus,
        AcademicRequirementStatus impactStatus,
        List<String> courseReferences,
        List<String> pendingCourseReferences,
        List<CourseImpactResponse> courses,
        LocalDateTime calculatedAt,
        String scopeNotice,
        String message) {
    public record SubjectReferenceResponse(Long subjectId, String subjectCode, String subjectName) {}

    public record PrerequisiteResponse(SubjectReferenceResponse subject, Boolean passedBeforeSemester) {}

    public record DependencyImpactResponse(
            SubjectReferenceResponse subject, List<SubjectReferenceResponse> path, AcademicRequirementStatus status) {}

    public record CourseImpactResponse(
            Long enrollmentId,
            SubjectReferenceResponse subject,
            boolean registeredInCurrentSemester,
            boolean hasPublishedGrade,
            boolean hasPassingResult,
            AcademicRequirementStatus prerequisiteStatus,
            List<PrerequisiteResponse> prerequisites,
            boolean failureAssumptionApplicable,
            AcademicRequirementStatus impactStatus,
            List<DependencyImpactResponse> directImpacts,
            List<DependencyImpactResponse> indirectImpacts) {}
}
