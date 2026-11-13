package com.eduhub.edu_hub.aiassistant.application.port;

import java.util.List;

public interface DocumentChunkingPort {
    List<KnowledgeDocumentPort.KnowledgeChunk> chunk(String text);
}
