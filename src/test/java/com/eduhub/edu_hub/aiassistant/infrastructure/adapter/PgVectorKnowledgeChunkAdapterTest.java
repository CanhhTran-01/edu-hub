package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort.ChunkContent;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.*;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class PgVectorKnowledgeChunkAdapterTest {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    NamedParameterJdbcTemplate namedJdbc = mock(NamedParameterJdbcTemplate.class);
    EmbeddingModel model = mock(EmbeddingModel.class);
    AiRagProperties properties = new AiRagProperties();
    Clock clock = Clock.fixed(Instant.parse("2026-11-18T17:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    PgVectorKnowledgeChunkAdapter adapter =
            new PgVectorKnowledgeChunkAdapter(jdbc, namedJdbc, model, properties, clock);

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void noAvailableSourceSkipsEmbeddingEntirely() {
        when(namedJdbc.queryForObject(anyString(), anyMap(), eq(Boolean.class))).thenReturn(false);
        assertThat(adapter.findRelevantChunks("Quy chế?", LocalDate.now(clock))).isEmpty();
        verifyNoInteractions(model);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(namedJdbc).queryForObject(sql.capture(), anyMap(), eq(Boolean.class));
        assertThat(sql.getValue())
                .contains(
                        "source.status = 'ACTIVE'",
                        "source.effective_date <= :onDate",
                        "source.expiry_date",
                        "source.embedding_model = :model",
                        "replacement.prev_source_id");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void queryUsesQueryTaskAndBindsSameModelProfileAsDocuments() {
        when(namedJdbc.queryForObject(anyString(), anyMap(), eq(Boolean.class))).thenReturn(true);
        when(model.call(any())).thenReturn(response());
        when(namedJdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());
        adapter.findRelevantChunks("Quy chế?", LocalDate.now(clock));
        var request = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(model).call(request.capture());
        var options = (GoogleGenAiTextEmbeddingOptions) request.getValue().getOptions();
        assertThat(options.getModel()).isEqualTo(properties.getEmbeddingModel());
        assertThat(options.getDimensions()).isEqualTo(768);
        assertThat(options.getTaskType()).isEqualTo(GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_QUERY);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void insertKeepsSourceProcessingTokenAndVectorsOnTheSameChunkRow() {
        when(model.call(any())).thenReturn(response());
        adapter.storeChunks(9L, "attempt", List.of(new ChunkContent("Nội dung quy chế.", 2, 10)));
        var sql = ArgumentCaptor.forClass(String.class);
        var parameters = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(sql.capture(), parameters.capture());
        assertThat(sql.getValue())
                .contains(
                        "insert into knowledge_chunks",
                        "source.status = 'PROCESSING'",
                        "source.processing_token = ?",
                        "for share of source")
                .doesNotContain("section_title");
        Object[] chunkParameters = (Object[]) parameters.getValue().getFirst();
        assertThat(chunkParameters).hasSize(12);
        assertThat(chunkParameters[0]).isEqualTo(9L);
        assertThat(chunkParameters[4]).isEqualTo(properties.getEmbeddingModel());
        assertThat(chunkParameters[6]).isEqualTo(10);
        assertThat(chunkParameters[7]).isEqualTo(2);
        assertThat(chunkParameters[10]).isEqualTo(9L);
        assertThat(chunkParameters[11]).isEqualTo("attempt");
        var request = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(model).call(request.capture());
        assertThat(((GoogleGenAiTextEmbeddingOptions) request.getValue().getOptions()).getTaskType())
                .isEqualTo(GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_DOCUMENT);
    }

    private EmbeddingResponse response() {
        float[] vector = new float[768];
        vector[0] = 1;
        return new EmbeddingResponse(List.of(new Embedding(vector, 0)));
    }
}
