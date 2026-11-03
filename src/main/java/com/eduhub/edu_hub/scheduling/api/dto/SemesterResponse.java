package com.eduhub.edu_hub.scheduling.api.dto;

import com.eduhub.edu_hub.scheduling.domain.enums.SemesterStatus;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SemesterResponse {
    private Long id;
    private String semesterCode; // ví dụ: HK_1_2026_2027
    private String semesterName; // ví dụ: Học kì 1 năm học 2026-2027
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate registrationStart;
    private LocalDate registrationEnd;
    private SemesterStatus status; // suy ra từ ngày hiện tại, không lưu trong database
    private Boolean isLocked; // khóa thông tin kỳ + lớp học phần
    private Boolean lockedGrade; // khóa điểm
}
