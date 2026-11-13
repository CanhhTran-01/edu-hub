package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeDocumentPort;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PgVectorKnowledgeDocumentAdapter implements KnowledgeDocumentPort {
    private final VectorStore vectorStore;

    @Override
    public void replace(String sourceTitle, List<KnowledgeChunk> chunks) {
        // Dùng expression builder để filename được bind an toàn, không ghép chuỗi filter thủ công.
        FilterExpressionBuilder filters = new FilterExpressionBuilder();
        vectorStore.delete(filters.eq("source", sourceTitle).build());
        vectorStore.add(
                chunks.stream().map(chunk -> document(sourceTitle, chunk)).toList());
    }

    private Document document(String sourceTitle, KnowledgeChunk chunk) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", sourceTitle);
        if (chunk.sectionTitle() != null) metadata.put("sectionTitle", chunk.sectionTitle());
        return Document.builder().text(chunk.text()).metadata(metadata).build();
    }
}
