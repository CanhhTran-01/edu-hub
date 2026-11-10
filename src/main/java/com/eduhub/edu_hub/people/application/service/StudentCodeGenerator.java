package com.eduhub.edu_hub.people.application.service;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.people.application.code.AdministrativeClassNames;
import com.eduhub.edu_hub.people.application.exception.ProfileCreationException;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.policy.StudentCodePolicy;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StudentCodeGenerator {
    private final StudentRepository studentRepository;
    private final CurriculumFacade.Majors curriculumMajors;

    @Value("${admission.student-code-format:[MaNganh][SttKhoa][SttLop][SttSVTrongLop]}")
    private String studentCodeFormat = "[MaNganh][SttKhoa][SttLop][SttSVTrongLop]";

    public String generateStudentCode(AdministrativeClass administrativeClass, int studentSequence) {
        return StudentCodePolicy.appendStudentSequence(prefix(administrativeClass), studentSequence);
    }

    public int allocateNextStudentSequence(AdministrativeClass administrativeClass) {
        prefix(administrativeClass);
        int lastIssuedSequence = administrativeClass.getLastStudentSequence();
        if (lastIssuedSequence == 0) {
            lastIssuedSequence =
                    studentRepository.findStudentCodesByAdministrativeClassId(administrativeClass.getId()).stream()
                            .filter(code -> code != null && code.matches(".*[0-9]{6}$"))
                            .mapToInt(code -> Integer.parseInt(code.substring(code.length() - 2)))
                            .max()
                            .orElse(0);
        }
        if (lastIssuedSequence >= 99) {
            throw new ProfileCreationException(ErrorCode.STUDENT_CODE_SEQUENCE_EXCEEDED);
        }
        administrativeClass.setLastStudentSequence(lastIssuedSequence + 1);
        return lastIssuedSequence + 1;
    }

    private String prefix(AdministrativeClass administrativeClass) {
        if (administrativeClass.getMajor() == null
                || administrativeClass.getMajor().getId() == null) {
            throw new ProfileCreationException(ErrorCode.INVALID_STUDENT_CODE_MAJOR);
        }
        String canonicalCode = curriculumMajors
                .requireMajor(administrativeClass.getMajor().getId())
                .majorCode();
        if (administrativeClass.getCohort() == null) {
            throw new ProfileCreationException(ErrorCode.INVALID_STUDENT_CODE_COHORT);
        }
        Integer configuredCohort = administrativeClass.getCohort().getCodeSequence();
        if (configuredCohort == null || configuredCohort < 1 || configuredCohort > 99) {
            throw new ProfileCreationException(ErrorCode.INVALID_STUDENT_CODE_COHORT);
        }
        int cohortNumber = configuredCohort;
        int classNumber = AdministrativeClassNames.sequence(administrativeClass.getClassName(), configuredCohort)
                .orElseThrow(() -> new ProfileCreationException(ErrorCode.INVALID_STUDENT_CODE_CLASS));
        return StudentCodePolicy.generatePrefix(canonicalCode, cohortNumber, classNumber, studentCodeFormat);
    }
}
