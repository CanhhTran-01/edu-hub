package com.eduhub.edu_hub.people.domain.model;

import com.eduhub.edu_hub.shared.common.BaseEntity;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "student_admissions")
@Getter
@Setter
public class StudentAdmission extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String applicationCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id")
    private AdmissionPlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id")
    private AdministrativeClass administrativeClass;

    private String citizenId;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String email;
    private String phone;
    private BigDecimal entranceScore;

    @Enumerated(EnumType.STRING)
    private ActivityStatus activityStatus = ActivityStatus.STUDYING;
}
