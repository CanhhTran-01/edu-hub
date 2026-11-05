package com.eduhub.edu_hub.scheduling.api.dto.request;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
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
