package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TokenDocumentChunkerTest {
    @Test
    void keepsShortDocumentTogetherRegardlessOfArticleHeadings() {
        var counter = new AiTokenCounter();
        var chunker = new TokenDocumentChunker(new AiRagProperties(), counter);
        String text = "Quy chế đào tạo.\nĐiều 1. Phạm vi\nÁp dụng cho sinh viên.\nĐiều 2. Hồ sơ\nNộp hồ sơ đúng hạn.";
        var chunks = chunker.chunk(text, null);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().content()).isEqualTo(text);
        assertThat(chunks.getFirst().pageNumber()).isNull();
        assertThat(chunks.getFirst().tokenCount()).isEqualTo(counter.countTokens(text));
    }

    @Test
    void splitsLongTextByConfiguredTokenBudgetAndKeepsContentOrderAndPdfPage() {
        var counter = new AiTokenCounter();
        var properties = new AiRagProperties();
        properties.setChunkSize(400);
        var chunker = new TokenDocumentChunker(properties, counter);
        String text = IntStream.rangeClosed(1, 80)
                .mapToObj(paragraphNumber -> "Nội dung đăng ký học phần phải đáp ứng đầy đủ điều kiện tiên quyết số "
                        + paragraphNumber + ".")
                .collect(Collectors.joining(" "));
        var chunks = chunker.chunk(text, 5);
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(String.join(
                        " ", chunks.stream().map(chunk -> chunk.content()).toList()))
                .isEqualTo(text);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.pageNumber()).isEqualTo(5);
            assertThat(chunk.tokenCount())
                    .isEqualTo(counter.countTokens(chunk.content()))
                    .isLessThanOrEqualTo(properties.getChunkSize());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    void blankTextDoesNotCreateChunks(String text) {
        var chunker = new TokenDocumentChunker(new AiRagProperties(), new AiTokenCounter());
        assertThat(chunker.chunk(text, 5)).isEmpty();
    }
}
