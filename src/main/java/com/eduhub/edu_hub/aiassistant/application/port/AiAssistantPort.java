package com.eduhub.edu_hub.aiassistant.application.port;

import com.eduhub.edu_hub.aiassistant.domain.enums.AiIntent;
import java.util.List;

public interface AiAssistantPort {
    QuestionAnalysis analyzeQuestion(String message, List<ConversationMessage> history);

    Answer answer(String question, List<ConversationMessage> history, List<KnowledgeChunkPort.RetrievedChunk> chunks);

    record QuestionAnalysis(AiIntent intent, String standaloneQuestion) {}

    record Answer(String content, List<Long> citationIds) {}

    record ConversationMessage(String role, String content) {}
}
