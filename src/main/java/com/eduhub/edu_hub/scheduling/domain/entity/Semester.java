package com.eduhub.edu_hub.scheduling.domain.entity;

import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "semesters",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_semesters_code", columnNames = "semester_code"),
            @UniqueConstraint(name = "uk_semesters_name", columnNames = "semester_name")
        })
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Semester extends BaseEntity {
    @Column(name = "semester_code", nullable = false, columnDefinition = "text")
    private String semesterCode;

    @Column(name = "semester_name", nullable = false, columnDefinition = "text")
    private String semesterName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "registration_start", nullable = false)
    private LocalDate registrationStart;

    @Column(name = "registration_end", nullable = false)
    private LocalDate registrationEnd;

    @Column(name = "is_locked", nullable = false)
    private boolean locked;

    @Column(name = "locked_grade", nullable = false)
    private boolean lockedGrade;
}
