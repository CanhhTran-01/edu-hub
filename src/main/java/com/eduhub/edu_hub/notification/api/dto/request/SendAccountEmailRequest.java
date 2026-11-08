package com.eduhub.edu_hub.notification.api.dto.request;

import com.eduhub.edu_hub.notification.domain.enums.EmailTemplateType;
import lombok.Builder;

/** Mật khẩu chỉ truyền trong bộ nhớ để gửi, không lưu vào bảng thông báo hoặc ghi log. */
@Builder
public record SendAccountEmailRequest(
        String recipient, String username, String temporaryPassword, EmailTemplateType templateType) {}
