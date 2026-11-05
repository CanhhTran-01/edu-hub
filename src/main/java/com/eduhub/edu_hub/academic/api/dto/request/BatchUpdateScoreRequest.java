package com.eduhub.edu_hub.academic.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class BatchUpdateScoreRequest {
    @NotNull
    private Long moduleClassId;

    private MultipartFile file; // Excel
}
