package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import com.eduhub.edu_hub.aiassistant.application.port.DocumentChunkingPort;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

/** Chia văn bản theo số token; giữ số trang do bộ đọc tệp cung cấp để định vị trích dẫn. */
@Component
public class TokenDocumentChunker implements DocumentChunkingPort {
    private final TokenTextSplitter tokenSplitter;
    private final AiTokenCounter tokenCounter;

    public TokenDocumentChunker(AiRagProperties properties, AiTokenCounter tokenCounter) {
        this.tokenCounter = tokenCounter;
        this.tokenSplitter = TokenTextSplitter.builder()
                .withChunkSize(properties.getChunkSize())
                .build();
    }

    @Override
    public List<KnowledgeChunkPort.ChunkContent> chunk(String text, Integer pageNumber) {
        if (text.isBlank()) return List.of();
        // TODO(DOCUMENT_STRUCTURE_CHUNKING): Chưa chia theo Điều/Chương/Mục/Khoản để giữ luồng nạp đơn giản;
        // chỉ bổ sung khi cần giữ ngữ cảnh theo cấu trúc và đã có tiêu chí đánh giá truy xuất.
        return tokenSplitter.apply(List.of(Document.builder().text(text.trim()).build())).stream()
                .filter(document ->
                        document.getText() != null && !document.getText().isBlank())
                .map(document -> new KnowledgeChunkPort.ChunkContent(
                        document.getText(), pageNumber, tokenCounter.countTokens(document.getText())))
                .toList();
    }
}
