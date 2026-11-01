package com.eduhub.edu_hub.scheduling.api.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SemesterResponse {
    private Long id;
    private String semesterCode; // ví dụ: HK12425
    private String semesterName; // ví dụ: Học kỳ 1 năm học 2024-2025
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate registrationStart;
    private LocalDate registrationEnd;
    private Boolean isLocked; // khóa thông tin kỳ + lớp học phần
    private Boolean lockedGrade; // khóa điểm
}
