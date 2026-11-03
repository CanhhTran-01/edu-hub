package com.eduhub.edu_hub.scheduling.domain.entity;

import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "module_classes",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_module_classes_code", columnNames = "module_class_code"),
            @UniqueConstraint(
                    name = "uk_module_classes_sequence",
                    columnNames = {"semester_id", "subject_id", "class_sequence"})
        })
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModuleClass extends BaseEntity {
    @Column(name = "module_class_code", nullable = false, columnDefinition = "text")
    private String moduleClassCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "lecturer_id")
    private Long lecturerId;

    @Column(name = "class_sequence", nullable = false)
    private int classSequence;

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

    @Column(name = "current_size", nullable = false)
    private int currentSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModuleClassStatus status;

    public void changeCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public void cancel() {
        this.status = ModuleClassStatus.CANCELLED;
    }
}
