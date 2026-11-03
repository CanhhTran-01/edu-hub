package com.eduhub.edu_hub.curriculum.domain.entity;

import com.eduhub.edu_hub.curriculum.domain.enums.CatalogStatus;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import com.eduhub.edu_hub.shared.enums.TrainingType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "curriculums")
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

    @Enumerated(EnumType.STRING)
    @Column(name = "training_type", nullable = false)
    private TrainingType trainingType;

    @Column(name = "duration_semesters", nullable = false)
    private int durationSemesters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CatalogStatus status;
}
