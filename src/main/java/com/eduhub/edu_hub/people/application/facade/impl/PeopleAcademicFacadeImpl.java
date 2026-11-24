package com.eduhub.edu_hub.people.application.facade.impl;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.people.api.dto.response.StudentAcademicProfileResponse;
import com.eduhub.edu_hub.people.application.exception.StudentProfileException;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeopleAcademicFacadeImpl implements PeopleFacade.Academic {
    private final StudentRepository studentRepository;
    private final CurriculumFacade.ReferenceData curriculumReferenceData;

    @Override
    public StudentAcademicProfileResponse findStudentByUserId(Long userId) {
        var student = studentRepository
                .findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new StudentProfileException(ErrorCode.STUDENT_NOT_FOUND));
        var administrativeClass = student.getAdministrativeClass();
        if (administrativeClass != null && administrativeClass.isDeleted()) administrativeClass = null;
        var cohort = administrativeClass == null ? null : administrativeClass.getCohort();
        if (cohort != null && cohort.isDeleted()) cohort = null;
        Long majorId = administrativeClass == null ? null : administrativeClass.getMajorId();
        var major = majorId == null
                ? null
                : curriculumReferenceData.findMajorsByIds(Set.of(majorId)).stream()
                        .filter(majorResponse -> !majorResponse.deleted())
                        .findFirst()
                        .orElse(null);
        return new StudentAcademicProfileResponse(
                student.getId(),
                student.getStudentCode(),
                student.getFullName(),
                major == null ? null : major.id(),
                major == null ? null : major.majorName(),
                cohort == null ? null : cohort.getId(),
                cohort == null ? null : cohort.getCohortCode(),
                administrativeClass == null ? null : administrativeClass.getClassName(),
                administrativeClass == null ? null : administrativeClass.getTrainingType(),
                cohort == null ? null : cohort.getStartYear(),
                student.getActivityStatus(),
                student.getTargetGpa(),
                student.getAcademicWarningStatus(),
                student.getAcademicWarningReason());
    }
}
