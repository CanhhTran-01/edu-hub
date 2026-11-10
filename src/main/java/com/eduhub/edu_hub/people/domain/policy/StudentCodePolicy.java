package com.eduhub.edu_hub.people.domain.policy;

import com.eduhub.edu_hub.people.application.exception.ProfileCreationException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import java.util.Locale;

/** Ghép MSSV từ các thành phần đã được service tải và cấp số, không phụ thuộc repository/facade. */
public final class StudentCodePolicy {
    public static final int MAXIMUM_STUDENT_SEQUENCE = 99;

    private StudentCodePolicy() {}

    public static String generatePrefix(
            String majorCode, Integer cohortSequence, int classSequence, String codeFormat) {
        if (majorCode == null || !majorCode.toUpperCase(Locale.ROOT).matches("[A-Z]+")) {
            throw new ProfileCreationException(ErrorCode.INVALID_STUDENT_CODE_MAJOR);
        }
        if (cohortSequence == null || cohortSequence < 1 || cohortSequence > 99) {
            throw new ProfileCreationException(ErrorCode.INVALID_STUDENT_CODE_COHORT);
        }
        if (classSequence < 1 || classSequence > 99) {
            throw new ProfileCreationException(ErrorCode.INVALID_STUDENT_CODE_CLASS);
        }
        String remainingFormat = codeFormat;
        if (remainingFormat == null) throw new ProfileCreationException(ErrorCode.VALIDATION_ERROR);
        for (String token : List.of("[MaNganh]", "[SttKhoa]", "[SttLop]", "[SttSVTrongLop]")) {
            if (!remainingFormat.contains(token)
                    || remainingFormat.indexOf(token) != remainingFormat.lastIndexOf(token)) {
                throw new ProfileCreationException(ErrorCode.VALIDATION_ERROR);
            }
            remainingFormat = remainingFormat.replace(token, "");
        }
        if (!remainingFormat.matches("[A-Za-z0-9_-]*")) throw new ProfileCreationException(ErrorCode.VALIDATION_ERROR);
        return codeFormat
                .replace("[MaNganh]", majorCode.toUpperCase(Locale.ROOT))
                .replace("[SttKhoa]", String.format(Locale.ROOT, "%02d", cohortSequence))
                .replace("[SttLop]", String.format(Locale.ROOT, "%02d", classSequence));
    }

    public static String appendStudentSequence(String prefix, int studentSequence) {
        if (studentSequence < 1 || studentSequence > MAXIMUM_STUDENT_SEQUENCE) {
            throw new ProfileCreationException(ErrorCode.STUDENT_CODE_SEQUENCE_EXCEEDED);
        }
        return prefix.replace("[SttSVTrongLop]", String.format(Locale.ROOT, "%02d", studentSequence));
    }
}
