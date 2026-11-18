package com.eduhub.edu_hub.scheduling.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class WeeklyTimetableRequest {
    @NotNull(message = "Cần chọn ngày thứ Hai bắt đầu tuần")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate weekStartDate;
}
