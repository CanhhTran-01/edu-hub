package com.eduhub.edu_hub.aiassistant.domain.enums;

/** Trạng thái thực thi, không phân loại ý định câu hỏi hoặc quyết định tool. */
public enum AdvisorRequestState {
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED,
    LIMIT_REACHED
}
