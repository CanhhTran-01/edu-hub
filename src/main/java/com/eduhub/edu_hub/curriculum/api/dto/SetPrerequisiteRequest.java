package com.eduhub.edu_hub.curriculum.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class SetPrerequisiteRequest {
    @NotNull
    private Long subjectId;

    @NotEmpty
    private List<Long> prerequisiteSubjectIds;
}
