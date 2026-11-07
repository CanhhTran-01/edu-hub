package com.eduhub.edu_hub.admission.domain.model;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "admission_plans")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AdmissionPlan extends BaseEntity {

    @Column(name = "major_id", nullable = false)
    private Long majorId;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    private int totalStudents;
    private int minCapacity;
    private int maxCapacity;
    private int reserveCapacity;
    private Integer selectedClassCount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private AdmissionPlanStatus state = AdmissionPlanStatus.DRAFT;

    private long revision;
    private String confirmationToken;
    private LocalDateTime processingUntil;
    private String exceptionReason;
}
