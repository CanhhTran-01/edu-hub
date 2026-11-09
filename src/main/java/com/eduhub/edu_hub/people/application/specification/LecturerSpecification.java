package com.eduhub.edu_hub.people.application.specification;

import com.eduhub.edu_hub.people.api.dto.request.LecturerSearchRequest;
import com.eduhub.edu_hub.people.domain.enums.LecturerStatus;
import com.eduhub.edu_hub.people.domain.model.Faculty;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Chỉ dựng điều kiện truy vấn giảng viên; không lọc trạng thái tài khoản liên kết. */
public final class LecturerSpecification {
    private LecturerSpecification() {}

    public static Specification<Lecturer> fromRequest(LecturerSearchRequest request, LecturerStatus status) {
        return Specification.allOf(
                notDeleted(),
                matchesKeyword(request.getKeyword()),
                hasLecturerCodeContaining(request.getLecturerCode()),
                hasFullNameContaining(request.getFullName()),
                belongsToFaculty(request.getFacultyId()),
                hasStatus(status));
    }

    public static Specification<Lecturer> notDeleted() {
        return (root, query, builder) -> builder.isFalse(root.get("deleted"));
    }

    public static Specification<Lecturer> matchesKeyword(String keyword) {
        return (root, query, builder) -> {
            if (keyword == null || keyword.isBlank()) return null;
            String keywordPattern = containsPattern(keyword);
            return builder.or(
                    builder.like(builder.lower(root.get("lecturerCode")), keywordPattern),
                    builder.like(builder.lower(root.get("fullName")), keywordPattern));
        };
    }

    public static Specification<Lecturer> hasLecturerCodeContaining(String lecturerCode) {
        return (root, query, builder) -> lecturerCode == null || lecturerCode.isBlank()
                ? null
                : builder.like(builder.lower(root.get("lecturerCode")), containsPattern(lecturerCode));
    }

    public static Specification<Lecturer> hasFullNameContaining(String fullName) {
        return (root, query, builder) -> fullName == null || fullName.isBlank()
                ? null
                : builder.like(builder.lower(root.get("fullName")), containsPattern(fullName));
    }

    public static Specification<Lecturer> belongsToFaculty(Long facultyId) {
        return (root, query, builder) -> {
            if (facultyId == null) return null;
            Join<Lecturer, Faculty> faculty = root.join("faculty", JoinType.INNER);
            return builder.and(builder.equal(faculty.get("id"), facultyId), builder.isFalse(faculty.get("deleted")));
        };
    }

    public static Specification<Lecturer> hasStatus(LecturerStatus status) {
        return (root, query, builder) -> status == null ? null : builder.equal(root.get("status"), status);
    }

    private static String containsPattern(String searchText) {
        return "%" + searchText.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
