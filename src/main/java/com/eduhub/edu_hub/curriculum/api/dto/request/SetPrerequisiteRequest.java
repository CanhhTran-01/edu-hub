package com.eduhub.edu_hub.curriculum.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.Data;

@Data
public class SetPrerequisiteRequest {
    @NotNull(message = "Danh sách môn tiên quyết không được null; dùng [] để gỡ hết")
    private List<@NotNull @Positive Long> prerequisiteSubjectIds;
}
