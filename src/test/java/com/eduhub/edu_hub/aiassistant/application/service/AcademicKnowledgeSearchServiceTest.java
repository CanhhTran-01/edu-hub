package com.eduhub.edu_hub.aiassistant.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort.RetrievedChunk;
import com.eduhub.edu_hub.aiassistant.infrastructure.adapter.AiTokenCounter;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class AcademicKnowledgeSearchServiceTest {
    KnowledgeChunkPort chunkPort = mock(KnowledgeChunkPort.class);
    AiTokenCounter tokenCounter = mock(AiTokenCounter.class);
    AiRagProperties properties = new AiRagProperties();
    Clock clock = Clock.fixed(Instant.parse("2026-11-18T17:30:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    AcademicKnowledgeSearchService knowledgeSearchService =
            new AcademicKnowledgeSearchService(chunkPort, properties, tokenCounter, clock);

    @Test
    void retrievalUsesNormalizedRedactedQueryAndTheCurrentVietnameseDate() {
        when(chunkPort.findRelevantChunks(anyString(), any())).thenReturn(List.of());

        assertThat(knowledgeSearchService.findRelevantKnowledge(
                        "  Quy chế   cho email sinhvien@example.com, CCCD 012345678901?  "))
                .isEmpty();

        verify(chunkPort)
                .findRelevantChunks(
                        "Quy chế cho email [đã ẩn email], CCCD [đã ẩn định danh]?", LocalDate.of(2026, 11, 19));
    }

    @Test
    void oversizedChunksDoNotDisplaceRelevantChunksWithinTheConfiguredCountLimit() {
        properties.setMaximumContextChunks(2);
        properties.setContextTokenLimit(128);
        var oversizedChunk = chunk(11L);
        var firstAcceptedChunk = chunk(12L);
        var secondAcceptedChunk = chunk(13L);
        var remainingChunk = chunk(14L);
        when(chunkPort.findRelevantChunks(anyString(), any()))
                .thenReturn(List.of(oversizedChunk, firstAcceptedChunk, secondAcceptedChunk, remainingChunk));
        when(tokenCounter.countTokens(
                        oversizedChunk.content() + oversizedChunk.sourceTitle() + oversizedChunk.effectiveDate()))
                .thenReturn(128);

        assertThat(knowledgeSearchService.findRelevantKnowledge("Quy chế học lại?"))
                .containsExactly(firstAcceptedChunk, secondAcceptedChunk);
    }

    @Test
    void combinedChunkContextCannotExceedTheTokenBudgetEvenBelowTheCountLimit() {
        properties.setMaximumContextChunks(3);
        properties.setContextTokenLimit(100);
        var firstChunk = chunk(21L);
        when(chunkPort.findRelevantChunks(anyString(), any())).thenReturn(List.of(firstChunk, chunk(22L), chunk(23L)));

        assertThat(knowledgeSearchService.findRelevantKnowledge("Quy chế học lại?"))
                .containsExactly(firstChunk);
    }

    private RetrievedChunk chunk(Long chunkId) {
        return new RetrievedChunk(
                chunkId, 1L, "Quy chế", null, "Quy định " + chunkId, 20, 0.9, LocalDate.of(2026, 1, 1));
    }
}
