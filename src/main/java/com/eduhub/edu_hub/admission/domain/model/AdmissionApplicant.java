package com.eduhub.edu_hub.admission.domain.model;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.*;

@Entity
@Table(name = "admission_applicants")
@Builder
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AdmissionApplicant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id")
    private AdmissionPlan plan;

    private int rowNumber;
    private String applicationCode;
    private String citizenId;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String email;
    private String phone;
    private BigDecimal entranceScore;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private AdmissionApplicantStatus state = AdmissionApplicantStatus.VALID;

    private String errorMessage;
    private String warningMessage;
    private boolean reuseAccount;

    @Column(name = "class_id")
    private Long administrativeClassId;

    @Column(name = "student_id")
    private Long studentId;

    public boolean isPending() {
        return state == AdmissionApplicantStatus.VALID || state == AdmissionApplicantStatus.FAILED;
    }

    public boolean isIncluded() {
        return isPending() || state == AdmissionApplicantStatus.ENROLLED;
    }
}
