package com.eduhub.edu_hub.aiassistant.application.port;

import com.eduhub.edu_hub.aiassistant.application.service.AdvisorChatExecution;
import java.util.List;

/** Ranh giới model Agent; protocol/tool signatures của provider chỉ nằm trong adapter. */
public interface AiAdvisorModelPort {
    AiAssistantPort.Answer answer(AdvisorChatExecution execution, List<AiAssistantPort.ConversationMessage> history);
}
