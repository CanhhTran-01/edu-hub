package com.eduhub.edu_hub.people.domain.model;

import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

import lombok.*;

@Entity
@Table(name = "admission_plans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionPlan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "major_id", nullable = false)
    private Major major;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    private int totalStudents;
    private int minCapacity;
    private int maxCapacity;
    private int reserveCapacity;
    private Integer selectedClassCount;

    @Enumerated(EnumType.STRING)
    private State state = State.DRAFT;

    private long revision;
    private String confirmationToken;
    private LocalDateTime processingUntil;
    private String exceptionReason;

    public enum State {
        DRAFT,
        CLASSES_READY,
        PREVIEWED,
        CONFIRMING,
        PARTIALLY_CONFIRMED,
        CONFIRMED
    }
}
