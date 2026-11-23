package com.eduhub.edu_hub.scheduling.api.mapper;

import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import com.eduhub.edu_hub.scheduling.domain.enums.SemesterStatus;
import com.eduhub.edu_hub.scheduling.domain.model.Semester;
import com.eduhub.edu_hub.scheduling.domain.policy.SemesterPolicy;
import java.time.LocalDate;

public final class SemesterResponseMapper {
    private SemesterResponseMapper() {}

    public static SemesterResponse toResponse(Semester semester, LocalDate today) {

        SemesterStatus status = SemesterPolicy.determineStatus(semester.getStartDate(), semester.getEndDate(), today);

        // TODO: isLocked và lockedGrade cần phân định rõ ràng, có thực sự cần isLocked không ?
        // NOTE: Quyết định hiện tại giữ isLocked cho nghiệp vụ kỳ học; hạn khóa điểm đã chuyển sang từng Enrollment.

        return SemesterResponse.builder()
                .id(semester.getId())
                .semesterCode(semester.getSemesterCode())
                .semesterName(semester.getSemesterName())
                .startDate(semester.getStartDate())
                .endDate(semester.getEndDate())
                .registrationStart(semester.getRegistrationStart())
                .registrationEnd(semester.getRegistrationEnd())
                .status(status)
                .isLocked(semester.isLocked())
                .build();
    }
}
