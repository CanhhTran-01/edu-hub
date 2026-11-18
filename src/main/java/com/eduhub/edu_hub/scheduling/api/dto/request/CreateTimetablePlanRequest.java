package com.eduhub.edu_hub.scheduling.api.dto.request;

import jakarta.validation.constraints.*;
import java.util.List;
import lombok.Data;

@Data
public class CreateTimetablePlanRequest {
    @NotNull(message = "Danh sách lớp giữ cố định không được null")
    private List<@NotNull @Positive Long> pinnedModuleClassIds = List.of();
}
