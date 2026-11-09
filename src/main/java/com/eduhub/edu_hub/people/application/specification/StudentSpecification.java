package com.eduhub.edu_hub.people.application.specification;

import com.eduhub.edu_hub.people.api.dto.request.StudentSearchRequest;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Chỉ dựng điều kiện truy vấn sinh viên; service kiểm tra đầu vào và tải dữ liệu. */
public final class StudentSpecification {
    private StudentSpecification() {}

    public static Specification<Student> fromRequest(StudentSearchRequest request, ActivityStatus activityStatus) {
        return Specification.allOf(
                notDeleted(),
                matchesKeyword(request.getKeyword()),
                belongsToAcademicScope(request.getClassId(), request.getMajorId(), request.getCohortId()),
                hasActivityStatus(activityStatus));
    }

    public static Specification<Student> notDeleted() {
        return (root, query, builder) -> builder.isFalse(root.get("deleted"));
    }

    public static Specification<Student> matchesKeyword(String keyword) {
        return (root, query, builder) -> {
            if (keyword == null || keyword.isBlank()) return null;
            String keywordPattern = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
            return builder.or(
                    builder.like(builder.lower(root.get("studentCode")), keywordPattern),
                    builder.like(builder.lower(root.get("fullName")), keywordPattern));
        };
    }

    public static Specification<Student> belongsToAcademicScope(Long classId, Long majorId, Long cohortId) {
        return (root, query, builder) -> {
            if (classId == null && majorId == null && cohortId == null) return null;

            Join<Student, AdministrativeClass> administrativeClass = root.join("administrativeClass", JoinType.LEFT);
            List<Predicate> scopePredicates = new ArrayList<>();
            if (classId != null) {
                scopePredicates.add(builder.equal(administrativeClass.get("id"), classId));
            }
            if (majorId != null) {
                scopePredicates.add(
                        builder.equal(administrativeClass.get("major").get("id"), majorId));
            }
            if (cohortId != null) {
                Join<AdministrativeClass, Cohort> cohort = administrativeClass.join("cohort", JoinType.LEFT);
                scopePredicates.add(builder.equal(cohort.get("id"), cohortId));
            }
            return builder.and(scopePredicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Student> hasActivityStatus(ActivityStatus activityStatus) {
        return (root, query, builder) ->
                activityStatus == null ? null : builder.equal(root.get("activityStatus"), activityStatus);
    }
}
