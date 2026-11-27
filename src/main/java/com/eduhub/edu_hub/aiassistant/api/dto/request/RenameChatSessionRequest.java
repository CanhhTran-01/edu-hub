package com.eduhub.edu_hub.aiassistant.api.dto.request;

import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.shared.common.TextsNormalizer;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/** Chỉ nhận tiêu đề mới; đổi tên không sửa trạng thái ghim hoặc nội dung hội thoại. */
@Getter
public class RenameChatSessionRequest {
    @NotBlank(message = "Tiêu đề hội thoại không được để trống")
    @Size(max = 255, message = "Tiêu đề hội thoại tối đa 255 ký tự")
    private String title;

    public void setTitle(Object title) {
        if (title != null && !(title instanceof String)) {
            throw new AiAssistantException(ErrorCode.VALIDATION_ERROR, "title", "Tiêu đề hội thoại phải là chuỗi JSON");
        }
        // Chuẩn hóa trước validation để tên tương đương không làm phát sinh cập nhật thừa.
        this.title = TextsNormalizer.normalizeOptionalText((String) title);
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignoredFieldValue) {
        throw new AiAssistantException(
                ErrorCode.VALIDATION_ERROR, field, "Trường không được phép khi đổi tên hội thoại: " + field);
    }
}
