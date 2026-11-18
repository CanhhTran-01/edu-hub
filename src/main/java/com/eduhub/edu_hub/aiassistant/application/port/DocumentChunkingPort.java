package com.eduhub.edu_hub.aiassistant.application.port;

import java.util.List;

public interface DocumentChunkingPort {
    List<KnowledgeChunkPort.ChunkContent> chunk(String text, Integer pageNumber);
}
