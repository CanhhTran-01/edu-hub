package com.eduhub.edu_hub.aiassistant.application.specification;

import com.eduhub.edu_hub.aiassistant.domain.enums.KnowledgeSourceStatus;
import com.eduhub.edu_hub.aiassistant.domain.model.KnowledgeSource;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class KnowledgeSourceSpecification {
    private KnowledgeSourceSpecification() {}

    public static Specification<KnowledgeSource> matches(String keyword, KnowledgeSourceStatus status) {
        return (source, query, builder) -> {
            var predicate = builder.isFalse(source.get("deleted"));
            if (status != null) predicate = builder.and(predicate, builder.equal(source.get("status"), status));
            if (keyword != null && !keyword.isBlank()) {
                String escapedKeyword = keyword.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                predicate = builder.and(
                        predicate, builder.like(builder.lower(source.get("title")), "%" + escapedKeyword + "%", '\\'));
            }
            return predicate;
        };
    }
}
