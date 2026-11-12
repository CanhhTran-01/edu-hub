package com.eduhub.edu_hub.scheduling.domain.enums;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Bảng ca cố định dùng chung khi hiển thị lịch nháp và lịch chính thức. */
@Getter
@RequiredArgsConstructor
public enum TeachingShift {
    FIRST(1, 1, 3, LocalTime.of(7, 0), LocalTime.of(9, 25)),
    SECOND(2, 4, 6, LocalTime.of(9, 30), LocalTime.of(11, 55)),
    THIRD(3, 7, 9, LocalTime.of(12, 35), LocalTime.of(15, 0)),
    FOURTH(4, 10, 12, LocalTime.of(15, 5), LocalTime.of(17, 30));

    private final int shiftNumber;
    private final int lessonOrderStart;
    private final int lessonOrderEnd;
    private final LocalTime startTime;
    private final LocalTime endTime;

    public static Optional<TeachingShift> findByShiftNumber(int shiftNumber) {
        return Arrays.stream(values())
                .filter(shift -> shift.shiftNumber == shiftNumber)
                .findFirst();
    }

    /** Không gán giờ cho lịch cũ chỉ khớp một phần ca hoặc chứa khoảng tiết chưa được quy định. */
    public static Optional<TeachingShift> findByLessonRange(int lessonOrderStart, int lessonOrderEnd) {
        return Arrays.stream(values())
                .filter(shift -> shift.lessonOrderStart == lessonOrderStart && shift.lessonOrderEnd == lessonOrderEnd)
                .findFirst();
    }
}
