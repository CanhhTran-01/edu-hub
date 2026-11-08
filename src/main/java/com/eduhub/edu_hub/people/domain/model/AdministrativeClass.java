package com.eduhub.edu_hub.people.domain.model;

import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "administrative_classes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AdministrativeClass extends BaseEntity {
    // TODO(CLASS_PREFIX_CONFIG): configurable prefixes/explicit renames will be added later.
    @Column(name = "class_name", nullable = false)
    private String className;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "major_id", nullable = false)
    private Major major;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    @Enumerated(EnumType.STRING)
    @Column(name = "training_type", nullable = false)
    private TrainingType trainingType;

    @Column(name = "min_capacity", nullable = false)
    private int minCapacity;

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

    @Column(name = "reserve_capacity", nullable = false)
    private int reserveCapacity;

    @Column(name = "last_student_sequence", nullable = false)
    private int lastStudentSequence;
}
