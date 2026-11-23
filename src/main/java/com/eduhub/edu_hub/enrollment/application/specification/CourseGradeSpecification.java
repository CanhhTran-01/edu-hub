package com.eduhub.edu_hub.enrollment.application.specification;

import com.eduhub.edu_hub.enrollment.api.dto.request.SearchCourseGradesRequest;
import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.enrollment.domain.model.Enrollment;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

/** Chỉ dựng predicate; danh tính khớp từ khóa được People cung cấp trước khi phân trang. */
public final class CourseGradeSpecification {
    private CourseGradeSpecification() {}

    public static Specification<Enrollment> fromRequest(SearchCourseGradesRequest request, Set<Long> studentIds) {
        return (root, query, builder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(builder.isFalse(root.get("deleted")));
            predicates.add(builder.notEqual(root.get("status"), EnrollmentStatus.DROPPED));
            if (studentIds != null)
                predicates.add(
                        studentIds.isEmpty()
                                ? builder.disjunction()
                                : root.get("studentId").in(studentIds));
            if (request.getSemesterId() != null)
                predicates.add(builder.equal(root.get("semesterId"), request.getSemesterId()));
            if (request.getSubjectId() != null)
                predicates.add(builder.equal(root.get("subjectId"), request.getSubjectId()));
            if (request.getModuleClassId() != null)
                predicates.add(builder.equal(root.get("moduleClassId"), request.getModuleClassId()));
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
