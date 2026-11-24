package com.eduhub.edu_hub.scheduling.application.specification;

import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.ClassSchedule;
import java.time.LocalDate;
import java.util.Collection;
import org.springframework.data.jpa.domain.Specification;

public final class ClassScheduleSpecification {
    private ClassScheduleSpecification() {}

    public static Specification<ClassSchedule> notDeletedAndModuleClassIdIn(Collection<Long> classIds) {
        return (root, query, builder) -> builder.and(
                builder.isFalse(root.get("deleted")),
                root.get("moduleClass").get("id").in(classIds));
    }

    public static Specification<ClassSchedule> effective() {
        return (root, query, builder) -> builder.and(
                builder.isFalse(root.get("deleted")),
                builder.isFalse(root.get("moduleClass").get("deleted")),
                builder.notEqual(root.get("moduleClass").get("status"), ModuleClassStatus.CANCELLED),
                builder.isFalse(root.get("moduleClass").get("semester").get("deleted")));
    }

    public static Specification<ClassSchedule> overlappingPeriodExcludingClasses(
            LocalDate startDate, LocalDate endDate, Collection<Long> replacedClassIds) {
        return effective()
                .and((root, query, builder) -> builder.and(
                        builder.not(root.get("moduleClass").get("id").in(replacedClassIds)),
                        builder.lessThanOrEqualTo(root.get("startDate"), endDate),
                        builder.greaterThanOrEqualTo(root.get("endDate"), startDate)));
    }

    public static Specification<ClassSchedule> unfinishedForSubject(Long subjectId, LocalDate today) {
        // lấy các lịch học của một môn học mà học kỳ chưa kết thúc
        return effective()
                .and((root, query, builder) -> builder.and(
                        builder.equal(root.get("moduleClass").get("subjectId"), subjectId),
                        builder.greaterThanOrEqualTo(
                                root.get("moduleClass").get("semester").get("endDate"), today)));
    }

    public static Specification<ClassSchedule> unfinishedForLecturer(Long lecturerId, LocalDate today) {
        return effective()
                .and((root, query, builder) -> builder.and(
                        builder.equal(root.get("moduleClass").get("lecturerId"), lecturerId),
                        builder.notEqual(root.get("moduleClass").get("status"), ModuleClassStatus.COMPLETED),
                        builder.greaterThanOrEqualTo(root.get("endDate"), today)));
    }
}
