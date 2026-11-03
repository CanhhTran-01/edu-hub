package com.eduhub.edu_hub.scheduling.api.dto;

import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModuleClassResponse {
    private Long id;
    private Long semesterId;
    private Long subjectId;
    private Long lecturerId;
    private int classSequence;
    private String dataSource;
    private String moduleClassCode;
    private String subjectCode;
    private String subjectName;
    private int credits;
    private String lecturerName;
    private String semesterCode;
    private int maxCapacity;
    private int currentSize;
    private ModuleClassStatus status; // UNSCHEDULED, SCHEDULED, OPEN, CANCELLED
    private List<ClassScheduleResponse> schedules;
}
