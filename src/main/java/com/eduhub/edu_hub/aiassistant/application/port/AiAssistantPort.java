package com.eduhub.edu_hub.aiassistant.application.port;

import java.util.List;

public interface AiAssistantPort {
    Answer answer(String question, List<ConversationMessage> history, List<KnowledgeChunkPort.RetrievedChunk> chunks);

    record Answer(String content, List<Long> citationIds) {}

    record ConversationMessage(String role, String content) {}
}
