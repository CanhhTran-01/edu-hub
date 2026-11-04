package com.eduhub.edu_hub.curriculum.domain.model;

import com.eduhub.edu_hub.curriculum.domain.enums.SubjectType;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "curriculum_subjects",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_curriculum_subjects_pair",
                        columnNames = {"curriculum_id", "subject_id"}))
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CurriculumSubject extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private Curriculum curriculum;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "recommend_semester", nullable = false)
    private int recommendSemester;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false)
    private SubjectType subjectType;
}
