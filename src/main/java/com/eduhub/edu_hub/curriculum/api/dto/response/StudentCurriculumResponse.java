package com.eduhub.edu_hub.curriculum.api.dto.response;

import com.eduhub.edu_hub.curriculum.domain.enums.SubjectType;
import java.util.List;

/** Kỳ trong lộ trình là số thứ tự, không phải ID kỳ học thực tế của Scheduling. */
public record StudentCurriculumResponse(
        Long curriculumId,
        String curriculumCode,
        String curriculumName,
        Long majorId,
        String majorName,
        Long cohortId,
        String cohortCode,
        String trainingType,
        int durationSemesters,
        int requiredGraduationCredits,
        int totalCatalogCredits,
        List<PlannedSemesterResponse> semesters) {

    public record PlannedSemesterResponse(int semesterNumber, List<PlannedSubjectResponse> subjects) {}

    public record PlannedSubjectResponse(
            Long subjectId, String subjectCode, String subjectName, int credits, SubjectType subjectType) {}
}
