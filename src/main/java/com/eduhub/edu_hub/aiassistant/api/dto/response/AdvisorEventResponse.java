package com.eduhub.edu_hub.aiassistant.api.dto.response;

import java.util.UUID;

/** Envelope SSE; sessionId có thể null cho tới khi hội thoại mới được lưu thành công. */
public record AdvisorEventResponse(UUID requestId, String sessionId, long sequence, Object data) {}
