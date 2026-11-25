package com.eduhub.edu_hub.curriculum.application.facade.impl;

import com.eduhub.edu_hub.curriculum.api.dto.response.CurriculumResponse;
import com.eduhub.edu_hub.curriculum.api.dto.response.CurriculumSubjectResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.curriculum.domain.model.Curriculum;
import com.eduhub.edu_hub.curriculum.domain.repository.CurriculumRepository;
import com.eduhub.edu_hub.curriculum.domain.repository.CurriculumSubjectRepository;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurriculumCatalogFacadeImpl implements CurriculumFacade.Catalog {
    private final CurriculumRepository curriculums;
    private final CurriculumSubjectRepository curriculumSubjects;
    private final PeopleFacade.Cohorts peopleCohorts;

    @Override
    public Optional<CurriculumResponse> findById(Long curriculumId) {
        return curriculums.findByIdAndDeletedFalse(curriculumId).map(this::toResponse);
    }

    @Override
    public Optional<CurriculumResponse> findCurriculum(Long majorId, Long cohortId, TrainingType trainingType) {
        return curriculums
                .findByMajor_IdAndCohortIdAndTrainingTypeAndDeletedFalse(majorId, cohortId, trainingType)
                .map(this::toResponse);
    }

    private CurriculumResponse toResponse(Curriculum curriculum) {
        var subjects =
                curriculumSubjects
                        .findAllByCurriculum_IdAndDeletedFalseOrderByRecommendSemesterAscIdAsc(curriculum.getId())
                        .stream()
                        .filter(link -> !link.getSubject().isDeleted())
                        .map(link -> CurriculumSubjectResponse.builder()
                                .subjectId(link.getSubject().getId())
                                .subjectCode(link.getSubject().getSubjectCode())
                                .subjectName(link.getSubject().getSubjectName())
                                .credits(link.getSubject().getCredits())
                                .recommendSemester(link.getRecommendSemester())
                                .subjectType(link.getSubjectType())
                                .build())
                        .toList();
        Long cohortId = curriculum.getCohortId();
        // ID lịch sử được giữ kể cả khóa đã xóa; mã hiển thị chỉ lấy từ khóa chưa xóa qua People.
        String cohortCode = cohortId == null
                ? null
                : peopleCohorts.findCohortsByIds(Set.of(cohortId)).stream()
                        .findFirst()
                        .map(cohort -> cohort.cohortCode())
                        .orElse(null);
        return CurriculumResponse.builder()
                .id(curriculum.getId())
                .curriculumCode(curriculum.getCurriculumCode())
                .curriculumName(curriculum.getCurriculumName())
                .majorId(curriculum.getMajor().getId())
                .majorName(curriculum.getMajor().getMajorName())
                .cohortId(cohortId)
                .cohortCode(cohortCode)
                .trainingType(curriculum.getTrainingType().name())
                .durationSemesters(curriculum.getDurationSemesters())
                .totalCredits(subjects.stream()
                        .mapToInt(CurriculumSubjectResponse::getCredits)
                        .sum())
                .requiredGraduationCredits(curriculum.getRequiredGraduationCredits())
                .subjects(subjects)
                .build();
    }
}
