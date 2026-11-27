package com.eduhub.edu_hub.aiassistant.api.dto.response;

import java.time.LocalDateTime;

public record ChatSessionSummaryResponse(
        String sessionId,
        String title,
        String topicType,
        boolean pinned,
        LocalDateTime startedAt,
        LocalDateTime lastMessageAt,
        String lastMessage) {}
