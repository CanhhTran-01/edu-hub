package com.eduhub.edu_hub.people.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class BatchImportStudentRequest {
    @NotNull
    private Long majorId;

    @NotNull
    private Long cohortId;

    private MultipartFile file; // Excel
}
