package com.eduhub.edu_hub.scheduling.api.mapper;

import com.eduhub.edu_hub.scheduling.api.dto.response.OpeningSubjectResponse;
import com.eduhub.edu_hub.scheduling.domain.model.AcademicPlanningSnapshot;
import com.eduhub.edu_hub.scheduling.domain.policy.OpeningDemandPolicy.SubjectDemand;

public final class OpeningSubjectResponseMapper {
    private OpeningSubjectResponseMapper() {}

    public static OpeningSubjectResponse toResponse(SubjectDemand demand, AcademicPlanningSnapshot planningSnapshot) {
        var subject = demand.subject();
        return OpeningSubjectResponse.builder()
                .subjectId(subject.subjectId())
                .subjectCode(subject.subjectCode())
                .subjectName(subject.subjectName())
                .credits(subject.credits())
                .onTimeStudentCount(demand.onTimeStudentCount())
                .retakeStudentCount(demand.retakeStudentCount())
                .missedStudentCount(demand.missedStudentCount())
                .expectedStudentCount(demand.expectedStudentCount())
                .recommendedClassCount(demand.recommendedClassCount())
                .dataSource(planningSnapshot.source())
                .historyComplete(planningSnapshot.historyComplete())
                .build();
    }
}
