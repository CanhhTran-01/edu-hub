package com.eduhub.edu_hub.scheduling.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.Data;

@Data
public class SubjectClassesRequest {
    @NotNull
    @Positive
    private Long subjectId;

    private boolean confirmAdditionalClasses;

    @NotEmpty
    private List<@NotNull @Valid CreateModuleClassRequest> classes;
}
