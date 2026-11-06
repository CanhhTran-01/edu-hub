package com.eduhub.edu_hub.people.domain.model;

import com.eduhub.edu_hub.people.domain.enums.AccountNotificationStatus;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "students")
@Getter
@Setter
public class Student extends BaseEntity {
    @Column(name = "user_id", unique = true)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private AdministrativeClass administrativeClass;

    @Column(name = "student_code", nullable = false, unique = true)
    private String studentCode;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    private String gender;

    private String email;

    private String phone;

    @Column(name = "citizen_id")
    private String citizenId;

    @Column(name = "entrance_score")
    private BigDecimal entranceScore;

    @Column(name = "target_gpa")
    private BigDecimal targetGpa;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_status", nullable = false)
    private ActivityStatus activityStatus;

    @Column(name = "academic_warning_status")
    private String academicWarningStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_notification_status")
    private AccountNotificationStatus accountNotificationStatus;

    @Column(name = "account_notification_error")
    private String accountNotificationError;

    @Column(name = "academic_warning_reason", columnDefinition = "text")
    private String academicWarningReason;
}
