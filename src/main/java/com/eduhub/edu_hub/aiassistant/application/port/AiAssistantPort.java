package com.eduhub.edu_hub.aiassistant.application.port;

import java.util.List;

public interface AiAssistantPort {
    Answer answer(String message, String conversationId);

    record Answer(String content, List<Citation> citations) {}

    record Citation(String sourceTitle, String sectionTitle, Integer pageNumber, String snippet) {}
}
