package com.eduhub.edu_hub.curriculum.application.specification;

import com.eduhub.edu_hub.curriculum.api.dto.SubjectSearchRequest;
import com.eduhub.edu_hub.curriculum.domain.model.Subject;
import com.eduhub.edu_hub.curriculum.domain.model.SubjectPrerequisite;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class SubjectSpecification {
    private SubjectSpecification() {}

    public static Specification<Subject> search(SubjectSearchRequest request) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isFalse(root.get("deleted")));

            if (StringUtils.hasText(request.getKeyword())) {
                String keyword = contains(request.getKeyword());
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("subjectCode")), keyword),
                        builder.like(builder.lower(root.get("subjectName")), keyword)));
            }
            if (StringUtils.hasText(request.getSubjectCode())) {
                predicates.add(
                        builder.like(builder.lower(root.get("subjectCode")), contains(request.getSubjectCode())));
            }
            if (StringUtils.hasText(request.getSubjectName())) {
                predicates.add(
                        builder.like(builder.lower(root.get("subjectName")), contains(request.getSubjectName())));
            }
            if (request.getCredits() != null) {
                predicates.add(builder.equal(root.get("credits"), request.getCredits()));
            }
            if (request.getStatus() != null) {
                predicates.add(builder.equal(root.get("status"), request.getStatus()));
            }
            if (request.isWithoutPrerequisites()) {
                var subquery = query.subquery(Long.class);
                var prerequisite = subquery.from(SubjectPrerequisite.class);
                subquery.select(prerequisite.get("id"))
                        .where(
                                builder.equal(prerequisite.get("subject"), root),
                                builder.isFalse(prerequisite.get("deleted")),
                                builder.isFalse(
                                        prerequisite.get("prerequisiteSubject").get("deleted")));
                predicates.add(builder.not(builder.exists(subquery)));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String contains(String value) {
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
