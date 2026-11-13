package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import com.eduhub.edu_hub.aiassistant.application.port.DocumentChunkingPort;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeDocumentPort;
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

    public LegalArticleDocumentChunker(AiRagProperties properties) {
        this.tokenSplitter = TokenTextSplitter.builder()
                .withChunkSize(properties.getChunkSize())
                .build();
    }

    @Override
    public List<KnowledgeDocumentPort.KnowledgeChunk> chunk(String text) {
        Matcher matcher = ARTICLE.matcher(text);
        List<Article> articles = new ArrayList<>();
        while (matcher.find()) articles.add(new Article(matcher.start(), matcher.group(1)));
        // Tài liệu không có cấu trúc Điều được chia toàn bộ theo token.
        if (articles.isEmpty()) return split(text, null);

        List<KnowledgeDocumentPort.KnowledgeChunk> chunks = new ArrayList<>();
        // Phần mở đầu trước Điều đầu tiên vẫn được giữ, nhưng không có sectionTitle.
        if (articles.getFirst().start() > 0) {
            chunks.addAll(split(text.substring(0, articles.getFirst().start()), null));
        }
        for (int index = 0; index < articles.size(); index++) {
            Article article = articles.get(index);
            int end = index + 1 < articles.size() ? articles.get(index + 1).start() : text.length();
            // Mỗi Điều giữ nguyên sectionTitle; Điều quá dài mới được TokenTextSplitter tách tiếp.
            chunks.addAll(split(text.substring(article.start(), end), article.title()));
        }
        return List.copyOf(chunks);
    }

    private List<KnowledgeDocumentPort.KnowledgeChunk> split(String text, String sectionTitle) {
        if (text.isBlank()) return List.of();
        return tokenSplitter.apply(List.of(Document.builder().text(text.trim()).build())).stream()
                .map(document -> new KnowledgeDocumentPort.KnowledgeChunk(document.getText(), sectionTitle))
                .toList();
    }

    private record Article(int start, String title) {}
}
