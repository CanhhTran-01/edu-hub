package com.eduhub.edu_hub.people.application.service;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.people.application.code.AdministrativeClassNames;
import com.eduhub.edu_hub.people.application.exception.ProfileCreationException;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.policy.StudentCodePolicy;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.systemconfig.api.dto.response.SystemConfigSnapshotResponse;
import com.eduhub.edu_hub.shared.systemconfig.application.service.SystemConfigService;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import com.eduhub.edu_hub.shared.systemconfig.domain.policy.CodePrefixMappingPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StudentCodeGenerator {
    private final StudentRepository studentRepository;
    private final CurriculumFacade.Majors curriculumMajors;

    private final SystemConfigService systemConfigService;

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
        if (administrativeClass.getMajorId() == null) {
            throw new ProfileCreationException(ErrorCode.INVALID_STUDENT_CODE_MAJOR);
        }
        var configuration = systemConfigService.getSnapshot();
        String majorPrefix = resolveMajorPrefix(administrativeClass.getMajorId(), configuration);
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
        return StudentCodePolicy.generatePrefix(
                majorPrefix, cohortNumber, classNumber, configuration.getString(SystemConfigKey.STUDENT_CODE_FORMAT));
    }

    private String resolveMajorPrefix(Long majorId, SystemConfigSnapshotResponse configuration) {
        var major = curriculumMajors.requireMajor(majorId);
        CodePrefixMappingPolicy.requireUnambiguousName(
                major.majorName(),
                curriculumMajors.findMajorsForClassNaming().stream()
                        .map(candidate -> candidate.majorName())
                        .toList(),
                SystemConfigKey.MAJOR_CODE_PREFIXES);
        // Tên ngành chỉ tạo khóa tra cứu; tiền tố MSSV luôn lấy từ mapping đã được quản trị thiết lập.
        return configuration.requireNameCodePrefix(SystemConfigKey.MAJOR_CODE_PREFIXES, major.majorName());
    }
}
