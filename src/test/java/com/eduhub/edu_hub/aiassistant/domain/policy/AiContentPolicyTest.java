package com.eduhub.edu_hub.aiassistant.domain.policy;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiContentPolicyTest {
    @Test
    void acceptsOnlyInlineReferencesThatMatchDeclaredAndSuppliedChunks() {
        assertThat(AiContentPolicy.hasValidCitations("Theo quy chế [K12].", List.of(12L), Set.of(12L, 13L)))
                .isTrue();
        assertThat(AiContentPolicy.hasValidCitations("Theo quy chế [K99].", List.of(99L), Set.of(12L)))
                .isFalse();
        assertThat(AiContentPolicy.hasValidCitations("Theo quy chế [K12].", List.of(13L), Set.of(12L, 13L)))
                .isFalse();
        assertThat(AiContentPolicy.hasValidCitations("Theo quy chế.", List.of(12L), Set.of(12L)))
                .isFalse();
        assertThat(AiContentPolicy.hasValidCitations("Không đủ thông tin.", List.of(), Set.of(12L)))
                .isFalse();
    }

    @Test
    void hidesObviousSensitiveTextWithoutDamagingReferenceCodes() {
        String sanitized = AiContentPolicy.redactPromptText(
                "CCCD: 012345678901; email sv@example.edu.vn; password=private; xem [K123456789012]");
        assertThat(sanitized)
                .doesNotContain("012345678901", "sv@example.edu.vn", "private")
                .contains("[K123456789012]");
        assertThat(AiContentPolicy.containsSensitiveDocumentContent("CCCD: 012345678901"))
                .isTrue();
        assertThat(AiContentPolicy.containsSensitiveDocumentContent("Điều 12. Hồ sơ cần căn cước công dân."))
                .isFalse();
    }

    @Test
    void normalizesUnicodeAndWhitespace() {
        assertThat(AiContentPolicy.normalizeQuestion("  Học\t phí \n thế nào?  "))
                .isEqualTo("Học phí thế nào?");
    }
}
