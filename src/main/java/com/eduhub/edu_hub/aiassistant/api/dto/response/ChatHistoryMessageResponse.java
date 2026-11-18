package com.eduhub.edu_hub.aiassistant.api.dto.response;

import com.eduhub.edu_hub.aiassistant.domain.enums.AiIntent;
import com.eduhub.edu_hub.aiassistant.domain.enums.ChatMessageRole;
import java.time.LocalDateTime;
import java.util.List;

public record ChatHistoryMessageResponse(
        String messageId,
        ChatMessageRole role,
        String content,
        AiIntent intent,
        LocalDateTime sentAt,
        List<CitationResponse> citations) {}
