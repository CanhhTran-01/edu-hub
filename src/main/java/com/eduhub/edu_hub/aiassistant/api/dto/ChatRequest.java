package com.eduhub.edu_hub.aiassistant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequest {
    private String sessionId; // null = tạo mới

    @NotBlank
    @Size(max = 2000)
    private String message;
}
