package com.eduhub.edu_hub.curriculum.domain.model;

import com.eduhub.edu_hub.curriculum.domain.enums.SubjectStatus;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;

@Entity
@Table(
        name = "subjects",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_subjects_code", columnNames = "subject_code"),
            @UniqueConstraint(name = "uk_subjects_name", columnNames = "subject_name")
        })
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subject extends BaseEntity {

    @Column(name = "subject_code", nullable = false, updatable = false, columnDefinition = "text")
    private String subjectCode;

    @Column(name = "subject_name", nullable = false, columnDefinition = "text")
    @Setter
    private String subjectName;

    @Column(nullable = false)
    @Setter
    private int credits;

    @Column(columnDefinition = "text")
    @Setter
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter
    private SubjectStatus status;
}
