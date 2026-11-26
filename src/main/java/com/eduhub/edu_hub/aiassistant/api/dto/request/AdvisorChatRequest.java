package com.eduhub.edu_hub.aiassistant.api.dto.request;

import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Data;

/** Định danh chủ sở hữu luôn từ principal */
@Data
public class AdvisorChatRequest {
    @Size(max = 20)
    private String sessionId;

    @NotNull
    private UUID clientRequestId;

    @NotBlank
    @Size(max = 2000)
    private String message;

    @JsonAnySetter
    public void rejectUnknownField(String field, Object fieldValue) {
        throw new AiAssistantException(ErrorCode.VALIDATION_ERROR, field, "Trường chưa được hỗ trợ: " + field);
    }
}
