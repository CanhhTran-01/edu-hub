package com.eduhub.edu_hub.aiassistant.application.port;

import com.eduhub.edu_hub.aiassistant.application.service.AdvisorChatExecution;
import java.util.List;

/** Ranh giới model Agent; protocol/tool signatures của provider chỉ nằm trong adapter. */
public interface AiAdvisorModelPort {
    Answer answer(AdvisorChatExecution execution, List<ConversationMessage> history);

    record Answer(String content, List<Long> citationIds) {}

    record ConversationMessage(String role, String content) {}
}
