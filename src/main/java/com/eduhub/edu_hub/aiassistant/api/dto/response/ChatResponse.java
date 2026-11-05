package com.eduhub.edu_hub.aiassistant.api.dto.response;

import com.eduhub.edu_hub.shared.enums.AiIntent;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatResponse {
    private String sessionId;
    private String messageId;
    private String content;
    private AiIntent intent; // KNOWLEDGE / GRADUATION / OUT_OF_SCOPE
    private List<CitationResponse> citations;
    private GraduationScenarioResponse scenario; // nếu là tư vấn tốt nghiệp
}
