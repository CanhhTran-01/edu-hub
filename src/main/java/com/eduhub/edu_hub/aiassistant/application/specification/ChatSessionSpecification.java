package com.eduhub.edu_hub.aiassistant.application.specification;

import com.eduhub.edu_hub.aiassistant.domain.model.ChatSession;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class ChatSessionSpecification {
    private ChatSessionSpecification() {}

    public static Specification<ChatSession> ownedSessions(
            Long userId, String keyword, LocalDate fromDate, LocalDate toDate) {
        return (session, query, builder) -> {
            var predicate =
                    builder.and(builder.equal(session.get("userId"), userId), builder.isFalse(session.get("deleted")));
            if (keyword != null && !keyword.isBlank()) {
                String escapedKeyword = keyword.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                predicate = builder.and(
                        predicate, builder.like(builder.lower(session.get("title")), "%" + escapedKeyword + "%", '\\'));
            }
            if (fromDate != null)
                predicate = builder.and(
                        predicate, builder.greaterThanOrEqualTo(session.get("startedAt"), fromDate.atStartOfDay()));
            if (toDate != null)
                predicate = builder.and(
                        predicate,
                        builder.lessThan(
                                session.get("startedAt"), toDate.plusDays(1).atStartOfDay()));
            return predicate;
        };
    }
}
