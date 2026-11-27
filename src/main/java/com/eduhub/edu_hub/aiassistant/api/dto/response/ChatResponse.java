package com.eduhub.edu_hub.aiassistant.api.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatResponse {
    private String sessionId;
    private String messageId;
    private String content;
    private List<CitationResponse> citations;
    private AdvisorMessagePayloadResponse payload;
}
