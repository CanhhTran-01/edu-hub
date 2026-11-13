package com.eduhub.edu_hub.aiassistant.application.port;

import java.util.List;

public interface KnowledgeDocumentPort {
    void replace(String sourceTitle, List<KnowledgeChunk> chunks);

    record KnowledgeChunk(String text, String sectionTitle) {}
}
