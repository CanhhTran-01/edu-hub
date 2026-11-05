package com.eduhub.edu_hub.scheduling.application.specification;

import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.ClassSchedule;
import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

public final class RoomScheduleSpecification {
    private RoomScheduleSpecification() {}

    public static Specification<ClassSchedule> inUnfinishedSemesters(Long roomId, LocalDate today) {
        return (root, query, builder) -> {
            var moduleClass = root.join("moduleClass");
            var semester = moduleClass.join("semester");

            return builder.and(
                    builder.equal(root.get("room").get("id"), roomId),
                    builder.isFalse(root.get("deleted")),
                    builder.isFalse(moduleClass.get("deleted")),
                    builder.notEqual(moduleClass.get("status"), ModuleClassStatus.CANCELLED),
                    builder.isFalse(semester.get("deleted")),
                    builder.greaterThanOrEqualTo(semester.get("endDate"), today));
        };
    }
}
