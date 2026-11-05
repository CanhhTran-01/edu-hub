package com.eduhub.edu_hub.scheduling.domain.model;

import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.*;

@Entity
@Table(name = "class_schedules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassSchedule extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_class_id", nullable = false)
    private ModuleClass moduleClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "lesson_order_start", nullable = false)
    private int lessonOrderStart;

    @Column(name = "lesson_order_end", nullable = false)
    private int lessonOrderEnd;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
}
