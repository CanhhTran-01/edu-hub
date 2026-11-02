package com.eduhub.edu_hub.scheduling.application.specification;

import com.eduhub.edu_hub.scheduling.api.dto.SemesterSearchRequest;
import com.eduhub.edu_hub.scheduling.domain.entity.Semester;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class SemesterSpecification {
    private SemesterSpecification() {}

    public static Specification<Semester> search(SemesterSearchRequest request, LocalDate today) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
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
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("semesterCode")), keyword, '\\'),
                        builder.like(builder.lower(root.get("semesterName")), keyword, '\\')));
            }
            if (request.getStatus() != null) {
                predicates.add(
                        switch (request.getStatus()) {
                            case UPCOMING -> builder.greaterThan(root.get("startDate"), today);
                            case ONGOING -> builder.and(
                                    builder.lessThanOrEqualTo(root.get("startDate"), today),
                                    builder.greaterThanOrEqualTo(root.get("endDate"), today));
                            case ENDED -> builder.lessThan(root.get("endDate"), today);
                        });
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Semester> overlappingStudyPeriod(LocalDate start, LocalDate end, Long excludedId) {
        return overlapping("startDate", "endDate", start, end, excludedId);
    }

    public static Specification<Semester> overlappingRegistrationPeriod(
            LocalDate start, LocalDate end, Long excludedId) {
        return overlapping("registrationStart", "registrationEnd", start, end, excludedId);
    }

    private static Specification<Semester> overlapping(
            String startField, String endField, LocalDate start, LocalDate end, Long excludedId) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(builder.isFalse(root.get("deleted")));
            predicates.add(builder.lessThanOrEqualTo(root.get(startField), end));
            predicates.add(builder.greaterThanOrEqualTo(root.get(endField), start));

            if (excludedId != null) {
                predicates.add(builder.notEqual(root.get("id"), excludedId));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
