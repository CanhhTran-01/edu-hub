package com.eduhub.edu_hub.notification.infrastructure.template;

import lombok.Builder;

/** Nội dung chỉ giữ trong bộ nhớ để gửi, không lưu vào hàng đợi hoặc ghi log. */
@Builder
public record RenderedEmail(String subject, String textBody, String htmlBody) {}
