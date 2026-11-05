package com.eduhub.edu_hub.curriculum.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubjectActionRequest {
    @NotBlank(message = "Lý do không được để trống")
    private String reason;

    private boolean confirmed;
}
