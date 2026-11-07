package com.eduhub.edu_hub.curriculum.application.facade;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.dto.*;
import com.eduhub.edu_hub.shared.enums.TrainingType;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CurriculumFacade {
    interface ReferenceData {
        List<MajorSimpleResponse> findMajorsByIds(Set<Long> ids);

        List<CurriculumAssignmentResponse> findCurriculumAssignments(Set<Long> majorIds, Set<Long> cohortIds);
    }

    interface Catalog {
        Optional<CurriculumResponse> findById(Long curriculumId);

        Optional<CurriculumResponse> findCurriculum(Long majorId, Long cohortId, TrainingType trainingType);
    }

    interface Majors {
        MajorResponse requireMajor(Long id);

        List<MajorResponse> findMajorsForClassNaming();
    }

    /** Read facet phục vụ mở lớp, không làm giả các capability chưa triển khai của Facade đầy đủ. */
    interface ClassOpening {
        List<CurriculumOpeningSubjectResponse> findPlanningSubjects();

        List<SubjectSimpleResponse> findSubjectsByIds(Set<Long> ids);

        List<SubjectSimpleResponse> requireOpenableSubjects(Set<Long> ids);
    }
}
