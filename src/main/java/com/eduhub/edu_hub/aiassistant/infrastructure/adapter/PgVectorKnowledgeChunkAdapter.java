package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.*;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.TaskType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Một bảng chunk chứa cả vector; điều kiện nguồn được đọc trực tiếp, không dùng bản sao status trong metadata. */
@Component
@RequiredArgsConstructor
public class PgVectorKnowledgeChunkAdapter implements KnowledgeChunkPort {
    public static final int VECTOR_DIMENSIONS = 768;
    private static final String AVAILABLE_SOURCE =
            """
            source.status = 'ACTIVE' and not source.is_deleted and not chunk.is_deleted
            and source.effective_date <= :onDate and (source.expiry_date is null or source.expiry_date >= :onDate)
            and source.embedding_model = :model and source.embedding_model_version = :modelVersion
            and chunk.embedding_model = :model and chunk.embedding_model_version = :modelVersion
            and chunk.embedding is not null
            and not exists (select 1 from knowledge_sources replacement
                where replacement.prev_source_id = source.id and replacement.status = 'ACTIVE'
                  and not replacement.is_deleted and replacement.effective_date <= :onDate)
            """;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final AiRagProperties properties;
    private final Clock clock;

    @Override
    public void storeChunks(Long sourceId, String processingToken, List<ChunkContent> chunks) {
        for (int batchStart = 0; batchStart < chunks.size(); batchStart += properties.getEmbeddingBatchSize()) {
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Tác vụ đã bị hủy");
            int batchEnd = Math.min(chunks.size(), batchStart + properties.getEmbeddingBatchSize());
            var batch = chunks.subList(batchStart, batchEnd);
            List<float[]> embeddings =
                    embedTexts(batch.stream().map(ChunkContent::content).toList(), TaskType.RETRIEVAL_DOCUMENT);
            List<Object[]> parameters = new ArrayList<>();
            var auditTimestamp = Timestamp.valueOf(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            for (int index = 0; index < batch.size(); index++) {
                var chunk = batch.get(index);
                parameters.add(new Object[] {
                    sourceId,
                    batchStart + index,
                    chunk.content(),
                    vectorLiteral(embeddings.get(index)),
                    properties.getEmbeddingModel(),
                    properties.getEmbeddingModelVersion(),
                    chunk.tokenCount(),
                    chunk.pageNumber(),
                    chunk.sectionTitle(),
                    auditTimestamp,
                    auditTimestamp,
                    sourceId,
                    processingToken
                });
            }
            // Nguồn PROCESSING chưa thể truy xuất; token ngăn worker cũ ghi sau khi nguồn bị xóa hoặc xử lý lại.
            jdbcTemplate.batchUpdate(
                    """
                    insert into knowledge_chunks(source_id, chunk_index, content, embedding, embedding_model,
                        embedding_model_version, token_count, page_number, section_title, created_at, updated_at, is_deleted)
                    select ?, ?, ?, ?::vector, ?, ?, ?, ?, ?, ?, ?, false
                    from knowledge_sources source
                    where source.id = ? and source.status = 'PROCESSING' and not source.is_deleted
                      and source.processing_token = ?
                    for share of source
                    """,
                    parameters);
        }
    }

    @Override
    public List<RetrievedChunk> findRelevantChunks(String question, LocalDate onDate) {
        // Không gọi embedding khi chưa có nguồn khả dụng; đây vẫn là nhánh không đủ căn cứ của UC-29.
        var parameters = profileParameters(onDate);
        Boolean hasSource = namedJdbcTemplate.queryForObject(
                "select exists(select 1 from knowledge_chunks chunk join knowledge_sources source on source.id = chunk.source_id where "
                        + AVAILABLE_SOURCE + ")",
                parameters,
                Boolean.class);
        if (!Boolean.TRUE.equals(hasSource)) return List.of();
        parameters.put(
                "embedding",
                vectorLiteral(
                        embedTexts(List.of(question), TaskType.RETRIEVAL_QUERY).getFirst()));
        parameters.put("minimumScore", properties.getSimilarityThreshold());
        parameters.put("topK", properties.getTopK());
        return namedJdbcTemplate.query(
                """
                select chunk.id, chunk.source_id, source.title, chunk.section_title, chunk.page_number,
                    chunk.content, chunk.token_count, source.effective_date,
                    1 - (chunk.embedding <=> cast(:embedding as vector)) as relevance_score
                from knowledge_chunks chunk join knowledge_sources source on source.id = chunk.source_id
                where """
                        + AVAILABLE_SOURCE
                        + """
                 and (chunk.embedding <=> cast(:embedding as vector)) <= (1 - :minimumScore)
                order by chunk.embedding <=> cast(:embedding as vector), chunk.id limit :topK
                """,
                parameters,
                (record, index) -> new RetrievedChunk(
                        record.getLong("id"),
                        record.getLong("source_id"),
                        record.getString("title"),
                        record.getString("section_title"),
                        record.getObject("page_number", Integer.class),
                        record.getString("content"),
                        record.getInt("token_count"),
                        record.getDouble("relevance_score"),
                        record.getObject("effective_date", LocalDate.class)));
    }

    @Override
    public Set<Long> lockAvailableChunkIds(List<Long> chunkIds, LocalDate onDate) {
        if (chunkIds.isEmpty()) return Set.of();
        var parameters = profileParameters(onDate);
        parameters.put("chunkIds", chunkIds);
        // Gọi trong transaction ghi lịch sử; khóa đọc nguồn đến commit để không lưu trích dẫn vừa bị vô hiệu hóa.
        return new HashSet<>(namedJdbcTemplate.queryForList(
                "select chunk.id from knowledge_chunks chunk join knowledge_sources source on source.id = chunk.source_id where "
                        + AVAILABLE_SOURCE
                        + " and chunk.id in (:chunkIds) order by source.id, chunk.id for share of source",
                parameters,
                Long.class));
    }

    @Override
    public void deleteUnpublishedChunks(Long sourceId) {
        jdbcTemplate.update(
                """
                delete from knowledge_chunks chunk where chunk.source_id = ?
                  and not exists (select 1 from message_chunk_references reference where reference.chunk_id = chunk.id)
                  and exists (select 1 from knowledge_sources source where source.id = chunk.source_id
                      and source.status in ('PROCESSING', 'FAILED'))
                """,
                sourceId);
    }

    @Override
    public void removeSourceVectors(Long sourceId) {
        jdbcTemplate.update(
                "update knowledge_chunks set embedding = null, is_deleted = true, updated_at = ? where source_id = ?",
                Timestamp.valueOf(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)),
                sourceId);
    }

    @Override
    public long countChunks(Long sourceId) {
        Long chunkCount = jdbcTemplate.queryForObject(
                "select count(*) from knowledge_chunks where source_id = ? and not is_deleted", Long.class, sourceId);
        return chunkCount == null ? 0 : chunkCount;
    }

    @Override
    public boolean hasCompleteIndex(Long sourceId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                """
                select count(*) > 0 and min(chunk_index) = 0 and max(chunk_index) = count(*) - 1
                    and bool_and(coalesce(embedding is not null and token_count > 0
                    and embedding_model = ? and embedding_model_version = ?, false))
                from knowledge_chunks where source_id = ? and not is_deleted
                """,
                Boolean.class,
                properties.getEmbeddingModel(),
                properties.getEmbeddingModelVersion(),
                sourceId));
    }

    private Map<String, Object> profileParameters(LocalDate onDate) {
        var parameters = new HashMap<String, Object>();
        parameters.put("onDate", onDate);
        parameters.put("model", properties.getEmbeddingModel());
        parameters.put("modelVersion", properties.getEmbeddingModelVersion());
        return parameters;
    }

    private List<float[]> embedTexts(List<String> texts, TaskType taskType) {
        var options = GoogleGenAiTextEmbeddingOptions.builder()
                .model(properties.getEmbeddingModel())
                .dimensions(VECTOR_DIMENSIONS)
                .taskType(taskType)
                .autoTruncate(false)
                .build();
        var embeddings = embeddingModel.call(new EmbeddingRequest(texts, options)).getResults().stream()
                .sorted(Comparator.comparingInt(Embedding::getIndex))
                .toList();
        if (embeddings.size() != texts.size()) throw new IllegalStateException("Dịch vụ embedding trả thiếu vector");
        List<float[]> vectors = new ArrayList<>();
        for (int index = 0; index < embeddings.size(); index++) {
            if (embeddings.get(index).getIndex() != index)
                throw new IllegalStateException("Thứ tự vector không hợp lệ");
            float[] vector = embeddings.get(index).getOutput();
            if (vector.length != VECTOR_DIMENSIONS)
                throw new IllegalStateException("Số chiều vector không phù hợp schema");
            double squaredLength = 0;
            for (float component : vector) {
                if (!Float.isFinite(component)) throw new IllegalStateException("Vector chứa thành phần không hữu hạn");
                squaredLength += (double) component * component;
            }
            if (squaredLength == 0) throw new IllegalStateException("Vector không có độ dài");
            vectors.add(vector);
        }
        return vectors;
    }

    private String vectorLiteral(float[] vector) {
        List<String> components = new ArrayList<>(vector.length);
        for (float component : vector) components.add(Float.toString(component));
        return components.stream().collect(Collectors.joining(",", "[", "]"));
    }
}
