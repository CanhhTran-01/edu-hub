package com.eduhub.edu_hub.curriculum.application.facade.impl;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.CurriculumResponse;
import com.eduhub.edu_hub.curriculum.application.facade.dto.CurriculumSubjectResponse;
import com.eduhub.edu_hub.curriculum.domain.model.Curriculum;
import com.eduhub.edu_hub.curriculum.domain.repository.CurriculumRepository;
import com.eduhub.edu_hub.curriculum.domain.repository.CurriculumSubjectRepository;
import com.eduhub.edu_hub.shared.enums.TrainingType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurriculumCatalogFacadeImpl implements CurriculumFacade.Catalog {
    private final CurriculumRepository curriculums;
    private final CurriculumSubjectRepository curriculumSubjects;

    @Override
    public Optional<CurriculumResponse> findById(Long curriculumId) {
        return curriculums.findByIdAndDeletedFalse(curriculumId).map(this::toResponse);
    }

    @Override
    public Optional<CurriculumResponse> findCurriculum(Long majorId, Long cohortId, TrainingType trainingType) {
        return curriculums
                .findByMajor_IdAndCohort_IdAndTrainingTypeAndDeletedFalse(majorId, cohortId, trainingType)
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
        var cohort = curriculum.getCohort();
        String cohortCode = cohort == null || cohort.isDeleted() ? null : cohort.getCohortCode();
        return CurriculumResponse.builder()
                .id(curriculum.getId())
                .curriculumCode(curriculum.getCurriculumCode())
                .curriculumName(curriculum.getCurriculumName())
                .majorId(curriculum.getMajor().getId())
                .majorName(curriculum.getMajor().getMajorName())
                .cohortId(cohort == null ? null : cohort.getId())
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
