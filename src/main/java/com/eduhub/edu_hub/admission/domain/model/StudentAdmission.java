package com.eduhub.edu_hub.admission.domain.model;

import com.eduhub.edu_hub.shared.common.BaseEntity;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.*;

@Entity
@Table(name = "student_admissions")
@Builder
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StudentAdmission extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String applicationCode;

    @Column(name = "student_id")
    private Long studentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id")
    private AdmissionPlan plan;

    @Column(name = "class_id")
    private Long administrativeClassId;

    private String citizenId;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String email;
    private String phone;
    private BigDecimal entranceScore;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private ActivityStatus activityStatus = ActivityStatus.STUDYING;
}
