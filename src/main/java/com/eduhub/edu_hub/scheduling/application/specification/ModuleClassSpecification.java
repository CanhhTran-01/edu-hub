package com.eduhub.edu_hub.scheduling.application.specification;

import com.eduhub.edu_hub.scheduling.api.dto.ModuleClassSearchRequest;
import com.eduhub.edu_hub.scheduling.domain.entity.ModuleClass;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class ModuleClassSpecification {
    private ModuleClassSpecification() {}

    public static Specification<ModuleClass> search(ModuleClassSearchRequest request) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.isFalse(root.get("deleted")));
            if (StringUtils.hasText(request.getKeyword())) {
                String keyword = "%"
                        + request.getKeyword()
                                .trim()
                                .toLowerCase(Locale.ROOT)
                                .replace("\\", "\\\\")
                                .replace("%", "\\%")
                                .replace("_", "\\_")
                        + "%";
                predicates.add(builder.like(builder.lower(root.get("moduleClassCode")), keyword, '\\'));
            }
            if (request.getSemesterId() != null)
                predicates.add(builder.equal(root.get("semester").get("id"), request.getSemesterId()));
            if (request.getSubjectId() != null)
                predicates.add(builder.equal(root.get("subjectId"), request.getSubjectId()));
            if (request.getLecturerId() != null)
                predicates.add(builder.equal(root.get("lecturerId"), request.getLecturerId()));
            if (request.getStatus() != null) predicates.add(builder.equal(root.get("status"), request.getStatus()));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
