package com.eduhub.edu_hub.enrollment.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EnrollmentResponse {
    private Long id;
    private Long studentId;
    private String studentCode;
    private String studentName;
    private Long moduleClassId;
    private String moduleClassCode;
    private String subjectCode;
    private String subjectName;
    private int credits;
    private LocalDateTime registeredAt;
    private String status; // REGISTERED / DROPPED / COMPLETED / FAILED
    // điểm (không có thì null)
    private BigDecimal componentScore1;
    private BigDecimal componentScore2;
    private BigDecimal processScore;
    private BigDecimal finalExamScore;
    private BigDecimal courseScore10;
    private String courseScoreLetter;
    private BigDecimal courseScore4;
}
