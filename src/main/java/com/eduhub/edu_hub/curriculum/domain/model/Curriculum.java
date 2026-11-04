package com.eduhub.edu_hub.curriculum.domain.model;

import com.eduhub.edu_hub.curriculum.domain.enums.CatalogStatus;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import com.eduhub.edu_hub.shared.enums.TrainingType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "curriculums",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_curriculums_major_cohort_training",
                        columnNames = {"major_id", "cohort_id", "training_type"}))
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Curriculum extends BaseEntity {
    @Column(name = "curriculum_code", nullable = false, columnDefinition = "text")
    private String curriculumCode;

    @Column(name = "curriculum_name", nullable = false, columnDefinition = "text")
    private String curriculumName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "major_id", nullable = false)
    private Major major;

    // CTĐT cũ chưa cấu hình khóa vẫn có thể để trống.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id")
    private Cohort cohort;

    @Enumerated(EnumType.STRING)
    @Column(name = "training_type", nullable = false)
    private TrainingType trainingType;

    @Column(name = "duration_semesters", nullable = false)
    private int durationSemesters;

    @Column(name = "required_graduation_credits", nullable = false)
    private int requiredGraduationCredits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CatalogStatus status;
}
