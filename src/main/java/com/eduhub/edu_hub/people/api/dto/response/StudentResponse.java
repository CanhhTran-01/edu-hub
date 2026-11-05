package com.eduhub.edu_hub.people.api.dto.response;

import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudentResponse {
    private Long id;
    private String studentCode;
    private String fullName;
    private LocalDate dateOfBirth;
    private String email;
    private String phone;
    private String citizenId;
    private String majorName;
    private String cohortCode;
    private String className;
    private String trainingType;
    private BigDecimal entranceScore;
    private BigDecimal targetGpa;
    private ActivityStatus activityStatus; // STUDYING, DEACTIVATED, DELETED
    private String academicWarningStatus;
    private String academicWarningReason;
    private String statusReason;
    private LocalDateTime createdAt;
}
