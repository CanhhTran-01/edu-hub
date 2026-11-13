package com.eduhub.edu_hub.aiassistant.api.dto.response;

import java.time.LocalDateTime;

public record ChatSessionSummaryResponse(
        String sessionId, LocalDateTime createdAt, LocalDateTime lastMessageAt, String lastMessage) {}
