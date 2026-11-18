package com.eduhub.edu_hub.aiassistant.application.port;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface KnowledgeChunkPort {
    void storeChunks(Long sourceId, String processingToken, List<ChunkContent> chunks);

    List<RetrievedChunk> findRelevantChunks(String question, LocalDate onDate);

    Set<Long> lockAvailableChunkIds(List<Long> chunkIds, LocalDate onDate);

    void deleteUnpublishedChunks(Long sourceId);

    void removeSourceVectors(Long sourceId);

    long countChunks(Long sourceId);

    boolean hasCompleteIndex(Long sourceId);

    record ChunkContent(String content, String sectionTitle, Integer pageNumber, int tokenCount) {}

    record RetrievedChunk(
            Long chunkId,
            Long sourceId,
            String sourceTitle,
            String sectionTitle,
            Integer pageNumber,
            String content,
            int tokenCount,
            double relevanceScore,
            LocalDate effectiveDate) {}
}
