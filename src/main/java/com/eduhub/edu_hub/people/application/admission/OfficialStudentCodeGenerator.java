package com.eduhub.edu_hub.people.application.admission;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OfficialStudentCodeGenerator implements StudentCodeGenerator {
    private static final Pattern MAJOR_CODE = Pattern.compile("^[A-Za-z]{2}$");
    private static final Pattern COHORT_CODE = Pattern.compile("^[A-Za-z]{2}(\\d{1,2})$");
    private static final Pattern CLASS_CODE = Pattern.compile("^[Ll](\\d{1,2})$");

    private final StudentRepository students;
    private final CurriculumFacade.Majors majors;

    @Override
    public String generate(AdministrativeClass administrativeClass, int sequenceNumber) {
        if (sequenceNumber < 1 || sequenceNumber > 99) {
            throw new BaseException(ErrorCode.STUDENT_CODE_SEQUENCE_EXCEEDED);
        }
        return prefix(administrativeClass) + String.format(Locale.ROOT, "%02d", sequenceNumber);
    }

    @Override
    public int nextSequence(AdministrativeClass administrativeClass) {
        String prefix = prefix(administrativeClass);
        int maximum = students.findStudentCodesByAdministrativeClassId(administrativeClass.getId()).stream()
                .filter(code -> code != null && code.matches(Pattern.quote(prefix) + "\\d{2}"))
                .mapToInt(code -> Integer.parseInt(code.substring(prefix.length())))
                .max()
                .orElse(0);
        if (maximum >= 99) {
            throw new BaseException(ErrorCode.STUDENT_CODE_SEQUENCE_EXCEEDED);
        }
        return maximum + 1;
    }

    private String prefix(AdministrativeClass administrativeClass) {
        if (administrativeClass.getMajor() == null
                || administrativeClass.getMajor().getId() == null) {
            throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_MAJOR);
        }
        String canonicalCode =
                majors.requireMajor(administrativeClass.getMajor().getId()).majorCode();
        String majorCode = java.util.Map.of("CNTT", "CT", "ATTT", "AT", "DTVT", "DT", "TTNT", "AI")
                .get(canonicalCode.toUpperCase(Locale.ROOT));
        if (majorCode == null || !MAJOR_CODE.matcher(majorCode).matches()) {
            throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_MAJOR);
        }
        if (administrativeClass.getCohort() == null) {
            throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_COHORT);
        }
        Matcher cohort = COHORT_CODE.matcher(administrativeClass.getCohort().getCohortCode());
        if (!cohort.matches()) {
            throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_COHORT);
        }
        Matcher classCode = CLASS_CODE.matcher(administrativeClass.getClassName());
        if (!classCode.matches()) {
            throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_CLASS);
        }
        int cohortNumber = Integer.parseInt(cohort.group(1));
        int classNumber = Integer.parseInt(classCode.group(1));
        return majorCode.toUpperCase(Locale.ROOT) + String.format(Locale.ROOT, "%02d%02d", cohortNumber, classNumber);
    }
}
