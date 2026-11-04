package com.eduhub.edu_hub.curriculum.application.facade;

import com.eduhub.edu_hub.curriculum.api.dto.CurriculumAssignmentResponse;
import com.eduhub.edu_hub.curriculum.api.dto.CurriculumOpeningSubjectResponse;
import com.eduhub.edu_hub.curriculum.api.dto.CurriculumResponse;
import com.eduhub.edu_hub.curriculum.api.dto.MajorSimpleResponse;
import com.eduhub.edu_hub.curriculum.api.dto.SubjectSimpleResponse;
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
        com.eduhub.edu_hub.curriculum.api.dto.MajorResponse requireMajor(Long id);
        java.util.Optional<com.eduhub.edu_hub.curriculum.api.dto.MajorResponse> findMajorByCode(String code);
    }
    /** Read facet phục vụ mở lớp, không làm giả các capability chưa triển khai của Facade đầy đủ. */
    interface ClassOpening {
        List<CurriculumOpeningSubjectResponse> findPlanningSubjects();

        List<SubjectSimpleResponse> findSubjectsByIds(Set<Long> ids);

        List<SubjectSimpleResponse> requireOpenableSubjects(Set<Long> ids);
    }
}
