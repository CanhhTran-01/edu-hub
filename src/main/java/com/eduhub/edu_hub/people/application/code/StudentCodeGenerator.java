package com.eduhub.edu_hub.people.application.code;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StudentCodeGenerator {
    private static final Pattern MAJOR_CODE = Pattern.compile("^[A-Z]+$");

    private final StudentRepository students;
    private final CurriculumFacade.Majors majors;

    @Value("${admission.student-code-format:[MaNganh][SttKhoa][SttLop][SttSVTrongLop]}")
    private String format = "[MaNganh][SttKhoa][SttLop][SttSVTrongLop]";

    public String generate(AdministrativeClass administrativeClass, int sequenceNumber) {
        if (sequenceNumber < 1 || sequenceNumber > 99) {
            throw new BaseException(ErrorCode.STUDENT_CODE_SEQUENCE_EXCEEDED);
        }
        return prefix(administrativeClass)
                .replace("[SttSVTrongLop]", String.format(Locale.ROOT, "%02d", sequenceNumber));
    }

    public int nextSequence(AdministrativeClass administrativeClass) {
        prefix(administrativeClass);
        int maximum = administrativeClass.getLastStudentSequence();
        if (maximum == 0) {
            maximum = students.findStudentCodesByAdministrativeClassId(administrativeClass.getId()).stream()
                    .filter(code -> code != null && code.matches(".*[0-9]{6}$"))
                    .mapToInt(code -> Integer.parseInt(code.substring(code.length() - 2)))
                    .max()
                    .orElse(0);
        }
        if (maximum >= 99) {
            throw new BaseException(ErrorCode.STUDENT_CODE_SEQUENCE_EXCEEDED);
        }
        administrativeClass.setLastStudentSequence(maximum + 1);
        return maximum + 1;
    }

    private String prefix(AdministrativeClass administrativeClass) {
        if (administrativeClass.getMajor() == null
                || administrativeClass.getMajor().getId() == null) {
            throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_MAJOR);
        }
        String canonicalCode =
                majors.requireMajor(administrativeClass.getMajor().getId()).majorCode();
        String majorCode = canonicalCode.toUpperCase(Locale.ROOT);
        if (majorCode == null || !MAJOR_CODE.matcher(majorCode).matches()) {
            throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_MAJOR);
        }
        if (administrativeClass.getCohort() == null) {
            throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_COHORT);
        }
        Integer configuredCohort = administrativeClass.getCohort().getCodeSequence();
        if (configuredCohort == null || configuredCohort < 1 || configuredCohort > 99) {
            throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_COHORT);
        }
        int cohortNumber = configuredCohort;
        int classNumber = AdministrativeClassNames.sequence(administrativeClass.getClassName(), configuredCohort)
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_STUDENT_CODE_CLASS));
        if (cohortNumber < 1) throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_COHORT);
        if (classNumber < 1) throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_CLASS);
        String remaining = format;
        for (String token : java.util.List.of("[MaNganh]", "[SttKhoa]", "[SttLop]", "[SttSVTrongLop]")) {
            if (remaining.indexOf(token) < 0 || remaining.indexOf(token) != remaining.lastIndexOf(token)) {
                throw new BaseException(ErrorCode.VALIDATION_ERROR);
            }
            remaining = remaining.replace(token, "");
        }
        if (!remaining.matches("[A-Za-z0-9_-]*")) throw new BaseException(ErrorCode.VALIDATION_ERROR);
        return format.replace("[MaNganh]", majorCode)
                .replace("[SttKhoa]", String.format(Locale.ROOT, "%02d", cohortNumber))
                .replace("[SttLop]", String.format(Locale.ROOT, "%02d", classNumber));
    }
}
