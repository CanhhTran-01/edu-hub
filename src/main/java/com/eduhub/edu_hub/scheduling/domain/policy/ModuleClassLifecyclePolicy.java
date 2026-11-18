package com.eduhub.edu_hub.scheduling.domain.policy;

import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.TimetableProblem.ExistingSession;
import java.time.LocalDate;
import java.util.List;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class ModuleClassLifecyclePolicy {

    // Công bố chuyển SCHEDULED sang OPEN; đăng ký/hủy kiểm tra OPEN và khoảng ngày ngay tại service.

    public static ModuleClassStatus determineNextStatus(
            ModuleClassStatus currentStatus,
            LocalDate registrationEnd,
            LocalDate today,
            Integer totalTeachingPeriods,
            List<ExistingSession> schedules,
            boolean hasLecturer) {

        ModuleClassStatus nextStatus = currentStatus;

        // Ngày đóng đăng ký được tính hết ngày theo giờ Việt Nam.
        if (currentStatus == ModuleClassStatus.OPEN && today.isAfter(registrationEnd)) {
            nextStatus = ModuleClassStatus.CLOSED;
        }
        if (nextStatus != ModuleClassStatus.CLOSED
                || !hasLecturer
                || totalTeachingPeriods == null
                || totalTeachingPeriods <= 0) {
            return nextStatus;
        }
        if (schedules.stream()
                .anyMatch(schedule -> schedule.dayOfWeek() < 2
                        || schedule.dayOfWeek() > 7
                        || schedule.lessonOrderStart() < 1
                        || schedule.lessonOrderStart() > 10
                        || (schedule.lessonOrderStart() - 1) % 3 != 0
                        || schedule.lessonOrderEnd() != schedule.lessonOrderStart() + 2)) return nextStatus;
        var occurrences = schedules.stream()
                .flatMap(schedule -> TimetablePolicy.occurrenceDates(schedule).stream())
                .toList();

        // Chỉ suy kết thúc giảng dạy từ lịch đủ buổi; không suy đã nhập điểm hay xác nhận buổi thực tế.
        if (occurrences.size() == TimetablePolicy.requiredSessions(totalTeachingPeriods)
                && occurrences.stream().distinct().count() == occurrences.size()
                && occurrences.stream().allMatch(date -> date.isBefore(today))) {
            return ModuleClassStatus.COMPLETED;
        }

        return nextStatus;
    }
}
