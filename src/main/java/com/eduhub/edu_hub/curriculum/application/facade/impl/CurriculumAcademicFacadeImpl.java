package com.eduhub.edu_hub.curriculum.application.facade.impl;

import com.eduhub.edu_hub.curriculum.api.dto.response.*;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.service.StudentCurriculumQueryService;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurriculumAcademicFacadeImpl implements CurriculumFacade.Academic {
    private final StudentCurriculumQueryService studentCurriculumQueryService;

    @Override
    public Optional<StudentCurriculumResponse> findCurriculum(Long majorId, Long cohortId, TrainingType trainingType) {
        return studentCurriculumQueryService.findCurriculum(majorId, cohortId, trainingType);
    }

    @Override
    public List<SubjectPrerequisitesResponse> findPrerequisitesBySubjectIds(Set<Long> subjectIds) {
        return studentCurriculumQueryService.findPrerequisitesBySubjectIds(subjectIds);
    }
}
