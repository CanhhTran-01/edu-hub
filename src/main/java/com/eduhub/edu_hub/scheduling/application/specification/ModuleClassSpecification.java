package com.eduhub.edu_hub.scheduling.application.specification;

import com.eduhub.edu_hub.scheduling.api.dto.request.ModuleClassSearchRequest;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.ModuleClass;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

@NoArgsConstructor
public final class ModuleClassSpecification {

    public static Specification<ModuleClass> matchesRosterCriteria(
            Long semesterId, Long permittedLecturerId, String keyword) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.isFalse(root.get("deleted")));
            predicates.add(builder.isFalse(root.get("semester").get("deleted")));
            predicates.add(builder.equal(root.get("semester").get("id"), semesterId));
            predicates.add(builder.notEqual(root.get("status"), ModuleClassStatus.CANCELLED));

            // Lecturer ID đã được service xác định từ User ID; client không thể mở rộng phạm vi này.
            // Nếu có permittedLecturerId, chỉ trả về các lớp của giảng viên đó
            if (permittedLecturerId != null)
                predicates.add(builder.equal(root.get("lecturer").get("id"), permittedLecturerId));
            if (StringUtils.hasText(keyword)) {
                String searchPattern = "%"
                        + keyword.trim()
                                .toLowerCase(Locale.ROOT)
                                .replace("\\", "\\\\")
                                .replace("%", "\\%")
                                .replace("_", "\\_")
                        + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("moduleClassCode")), searchPattern, '\\'),
                        builder.like(builder.lower(root.get("subject").get("subjectCode")), searchPattern, '\\'),
                        builder.like(builder.lower(root.get("subject").get("subjectName")), searchPattern, '\\')));
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

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
                predicates.add(builder.equal(root.get("subject").get("id"), request.getSubjectId()));
            if (request.getLecturerId() != null)
                predicates.add(builder.equal(root.get("lecturer").get("id"), request.getLecturerId()));
            if (request.getStatus() != null) predicates.add(builder.equal(root.get("status"), request.getStatus()));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
