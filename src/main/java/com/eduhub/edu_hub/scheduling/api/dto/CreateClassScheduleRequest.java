package com.eduhub.edu_hub.scheduling.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

@Data
public class CreateClassScheduleRequest {
    @NotNull
    private Long moduleClassId;

    @NotNull
    private Long roomId;

    @Min(2)
    @Max(8)
    private int dayOfWeek;

    @Min(1)
    private int lessonOrderStart;

    @Min(1)
    private int lessonOrderEnd;

    private LocalDate startDate;
    private LocalDate endDate;
}
