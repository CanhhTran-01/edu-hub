package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import org.junit.jupiter.api.Test;

class LegalArticleDocumentChunkerTest {
    @Test
    void preservesIntroductionArticlesAndPdfPositionWithActualTokenCounts() {
        var counter = new AiTokenCounter();
        var chunker = new LegalArticleDocumentChunker(new AiRagProperties(), counter);
        var chunks = chunker.chunk(
                "Quy chế đào tạo.\nĐiều 1. Phạm vi\nÁp dụng cho sinh viên.\nĐiều 2. Hồ sơ\nNộp hồ sơ đúng hạn.", 5);
        assertThat(chunks).hasSize(3);
        assertThat(chunks.getFirst().sectionTitle()).isNull();
        assertThat(chunks.get(1).sectionTitle()).isEqualTo("Điều 1");
        assertThat(chunks.get(2).sectionTitle()).isEqualTo("Điều 2");
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.pageNumber()).isEqualTo(5);
            assertThat(chunk.tokenCount()).isEqualTo(counter.countTokens(chunk.content()));
        });
    }
}
