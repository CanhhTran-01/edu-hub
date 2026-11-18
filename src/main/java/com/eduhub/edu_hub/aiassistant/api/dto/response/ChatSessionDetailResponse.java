package com.eduhub.edu_hub.aiassistant.api.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ChatSessionDetailResponse(
        String sessionId,
        String title,
        String topicType,
        LocalDateTime startedAt,
        List<ChatHistoryMessageResponse> messages) {}
