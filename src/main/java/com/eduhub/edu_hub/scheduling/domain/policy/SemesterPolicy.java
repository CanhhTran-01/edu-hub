package com.eduhub.edu_hub.scheduling.domain.policy;

import com.eduhub.edu_hub.scheduling.application.exception.SemesterException;
import com.eduhub.edu_hub.scheduling.domain.enums.SemesterStatus;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SemesterPolicy {
    private static final Pattern SEMESTER_NAME =
            Pattern.compile("^Học kì ([1-9][0-9]*) năm học ([0-9]{4})-([0-9]{4})$");

    private SemesterPolicy() {}

    public static String normalizeSemesterName(String rawSemesterName) {
        if (rawSemesterName == null) {
            throw new SemesterException(ErrorCode.INVALID_SEMESTER_NAME);
        }

        String semesterName = rawSemesterName.trim();
        Matcher matcher = SEMESTER_NAME.matcher(semesterName);
        if (!matcher.matches()) {
            throw new SemesterException(ErrorCode.INVALID_SEMESTER_NAME);
        }

        int firstYear = Integer.parseInt(matcher.group(2));
        int secondYear = Integer.parseInt(matcher.group(3));

        if (firstYear < 1 || secondYear != firstYear + 1) {
            throw new SemesterException(ErrorCode.INVALID_SEMESTER_NAME);
        }

        return semesterName;
    }

    public static String generateSemesterCode(String semesterName) {
        return SEMESTER_NAME.matcher(semesterName).replaceFirst("HK_$1_$2_$3");
    }

    public static void validateDates(
            LocalDate semesterStart, LocalDate semesterEnd, LocalDate registrationStart, LocalDate registrationEnd) {
        if (semesterStart == null || semesterEnd == null || registrationStart == null || registrationEnd == null) {
            throw new SemesterException(ErrorCode.VALIDATION_ERROR);
        }

        // kiểm tra khoảng thời gian hợp lệ
        // registrationStart < registrationEnd < semesterStart < semesterEnd
        if (!semesterStart.isBefore(semesterEnd)) {
            throw new SemesterException(ErrorCode.INVALID_SEMESTER_DATES);
        }
        if (!registrationStart.isBefore(registrationEnd) || !registrationEnd.isBefore(semesterStart)) {
            throw new SemesterException(ErrorCode.INVALID_REGISTRATION_PERIOD);
        }
    }

    public static SemesterStatus determineStatus(LocalDate startDate, LocalDate endDate, LocalDate today) {
        // lấy giờ trong clock, check và cập nhật động trạng thái kì
        return today.isBefore(startDate)
                ? SemesterStatus.UPCOMING
                : today.isAfter(endDate) ? SemesterStatus.ENDED : SemesterStatus.ONGOING;
    }

    public static void requireClassChangesAllowed(LocalDate endDate, boolean locked, LocalDate today) {
        // kỳ học đã kết thúc
        if (today.isAfter(endDate)) {
            throw new SemesterException(ErrorCode.SEMESTER_ENDED);
        }
        // kì học đã khóa
        if (locked) {
            throw new SemesterException(ErrorCode.SEMESTER_LOCKED);
        }
    }
}
