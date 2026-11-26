package com.eduhub.edu_hub.aiassistant.application.service;

import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort.RetrievedChunk;
import com.eduhub.edu_hub.aiassistant.domain.policy.AiContentPolicy;
import com.eduhub.edu_hub.aiassistant.infrastructure.adapter.AiTokenCounter;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Pipeline truy xuất chung cho chat RAG và tool, giữ cùng bộ lọc nguồn và ngân sách đoạn trích. */
@Service
@RequiredArgsConstructor
public class AcademicKnowledgeSearchService {
    private final KnowledgeChunkPort chunkPort;
    private final AiRagProperties properties;
    private final AiTokenCounter tokenCounter;
    private final Clock clock;

    public List<RetrievedChunk> findRelevantKnowledge(String question) {
        String searchQuery = AiContentPolicy.redactPromptText(AiContentPolicy.normalizeQuestion(question));
        var selectedChunks = new ArrayList<RetrievedChunk>();
        int contextTokens = 0;
        for (var chunk : chunkPort.findRelevantChunks(searchQuery, LocalDate.now(clock))) {
            int chunkTokens =
                    tokenCounter.countTokens(chunk.content() + chunk.sourceTitle() + chunk.effectiveDate()) + 64;
            if (selectedChunks.size() >= properties.getMaximumContextChunks()) break;
            if (contextTokens + chunkTokens > properties.getContextTokenLimit()) continue;
            selectedChunks.add(chunk);
            contextTokens += chunkTokens;
        }
        return List.copyOf(selectedChunks);
    }
}
