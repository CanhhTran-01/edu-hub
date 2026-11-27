package com.eduhub.edu_hub.aiassistant.api.dto.request;

import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/** Chỉ nhận trạng thái ghim; chủ sở hữu và phiên được xác định từ principal và URL. */
@Getter
public class UpdateChatSessionPinRequest {
    @NotNull(message = "Trạng thái ghim không được để trống")
    private Boolean pinned;

    public void setPinned(Object pinned) {
        // Chỉ nhận boolean JSON để tránh chuỗi hoặc số vô tình thay đổi trạng thái ghim.
        if (pinned != null && !(pinned instanceof Boolean)) {
            throw new AiAssistantException(
                    ErrorCode.VALIDATION_ERROR, "pinned", "Trạng thái ghim phải là boolean JSON");
        }
        this.pinned = (Boolean) pinned;
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignoredFieldValue) {
        throw new AiAssistantException(
                ErrorCode.VALIDATION_ERROR, field, "Trường không được phép khi ghim hội thoại: " + field);
    }
}
