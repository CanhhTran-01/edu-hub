package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import com.eduhub.edu_hub.aiassistant.application.port.DocumentChunkingPort;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

@Component
public class LegalArticleDocumentChunker implements DocumentChunkingPort {
    private static final Pattern ARTICLE = Pattern.compile("(?m)^\\s*(Điều\\s+\\d+)\\.");
    private final TokenTextSplitter tokenSplitter;
    private final AiTokenCounter tokenCounter;

    public LegalArticleDocumentChunker(AiRagProperties properties, AiTokenCounter tokenCounter) {
        this.tokenCounter = tokenCounter;
        this.tokenSplitter = TokenTextSplitter.builder()
                .withChunkSize(properties.getChunkSize())
                .build();
    }

    private record Article(int start, String title) {}

    @Override
    public List<KnowledgeChunkPort.ChunkContent> chunk(String text, Integer pageNumber) {
        Matcher matcher = ARTICLE.matcher(text);
        List<Article> articles = new ArrayList<>();
        while (matcher.find()) articles.add(new Article(matcher.start(), matcher.group(1)));

        // Tài liệu không có cấu trúc Điều được chia toàn bộ theo token.
        if (articles.isEmpty()) return split(text, null, pageNumber);

        List<KnowledgeChunkPort.ChunkContent> chunks = new ArrayList<>();

        // Phần mở đầu trước Điều đầu tiên vẫn được giữ, nhưng không có sectionTitle.
        if (articles.getFirst().start() > 0) {
            chunks.addAll(split(text.substring(0, articles.getFirst().start()), null, pageNumber));
        }

        for (int index = 0; index < articles.size(); index++) {
            Article article = articles.get(index);
            int end = index + 1 < articles.size() ? articles.get(index + 1).start() : text.length();
            // Mỗi Điều giữ nguyên sectionTitle; Điều quá dài mới được TokenTextSplitter tách tiếp.
            chunks.addAll(split(text.substring(article.start(), end), article.title(), pageNumber));
        }

        return List.copyOf(chunks);
    }

    private List<KnowledgeChunkPort.ChunkContent> split(String text, String sectionTitle, Integer pageNumber) {
        if (text.isBlank()) return List.of();
        return tokenSplitter.apply(List.of(Document.builder().text(text.trim()).build())).stream()
                .filter(document ->
                        document.getText() != null && !document.getText().isBlank())
                .map(document -> new KnowledgeChunkPort.ChunkContent(
                        document.getText(), sectionTitle, pageNumber, tokenCounter.countTokens(document.getText())))
                .toList();
    }
}
