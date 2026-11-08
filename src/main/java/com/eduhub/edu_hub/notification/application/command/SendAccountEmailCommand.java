package com.eduhub.edu_hub.notification.application.command;

import com.eduhub.edu_hub.notification.domain.enums.EmailTemplateType;
import lombok.Builder;

/** Mật khẩu chỉ truyền trong bộ nhớ để gửi, không lưu vào bảng thông báo hoặc ghi log. */
@Builder
public record SendAccountEmailCommand(
        String recipient, String username, String temporaryPassword, EmailTemplateType templateType) {}
