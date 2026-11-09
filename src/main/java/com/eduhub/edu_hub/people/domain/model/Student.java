package com.eduhub.edu_hub.people.domain.model;

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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "students")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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

    private String phone;

    @Column(name = "citizen_id")
    private String citizenId;

    @Column(name = "entrance_score")
    private BigDecimal entranceScore;

    // TODO(STUDENT_TARGET_GPA_AI): Khi triển khai AI tư vấn tốt nghiệp, cung cấp mục tiêu qua People facade.
    @Column(name = "target_gpa")
    private BigDecimal targetGpa; // null là chưa thiết lập, không tự thay bằng 0.

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_status", nullable = false)
    private ActivityStatus activityStatus;

    @Column(name = "academic_warning_status")
    private String academicWarningStatus;

    @Column(name = "academic_warning_reason", columnDefinition = "text")
    private String academicWarningReason;
}
