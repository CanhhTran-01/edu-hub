package com.eduhub.edu_hub.curriculum.application.facade;

import com.eduhub.edu_hub.curriculum.api.dto.response.MajorSimpleResponse;
import com.eduhub.edu_hub.curriculum.api.dto.response.StudentCurriculumResponse;
import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectPrerequisitesResponse;
import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectResponse;
import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.dto.*;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CurriculumFacade {
    interface Academic {
        Optional<StudentCurriculumResponse> findCurriculum(Long majorId, Long cohortId, TrainingType trainingType);

        List<SubjectPrerequisitesResponse> findPrerequisitesBySubjectIds(Set<Long> subjectIds);
    }

    interface Registration {
        List<SubjectResponse> requireRegistrationSubjects(Set<Long> subjectIds);
    }

    interface TeachingSubjects {
        List<SubjectSimpleResponse> requireActiveTeachingSubjects(Set<Long> subjectIds);

        List<SubjectSimpleResponse> findTeachingSubjectsByIds(Set<Long> subjectIds);
    }

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
        /** Tìm cả môn lịch sử theo mã/tên để Scheduling lọc lớp trước khi phân trang. */
        Set<Long> findSubjectIdsByKeyword(String keyword);

        List<CurriculumOpeningSubjectResponse> findPlanningSubjects();

        List<SubjectSimpleResponse> findSubjectsByIds(Set<Long> ids);

        List<SubjectSimpleResponse> requireOpenableSubjects(Set<Long> ids);
    }
}
