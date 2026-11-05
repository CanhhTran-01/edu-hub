package com.eduhub.edu_hub.scheduling.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.Data;

@Data
public class CreateModuleClassesRequest {
    @NotEmpty
    private List<@NotNull @Valid SubjectClassesRequest> subjects;
}
