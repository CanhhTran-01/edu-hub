package com.eduhub.edu_hub.enrollment.domain.model;

import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

/** Một lượt học; hủy giữ lịch sử, đăng ký lại tạo lượt mới với tín chỉ đang áp dụng. */
@Entity
@Table(name = "enrollments")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Enrollment extends BaseEntity {
    @Column(nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private Long moduleClassId;

    @Column(nullable = false)
    private Long subjectId;

    @Column(nullable = false)
    private Long semesterId;

    @Column(nullable = false)
    private String subjectCode;

    @Column(nullable = false)
    private String subjectName;

    @Column(nullable = false)
    private String moduleClassCode;

    @Column(nullable = false)
    private int academicYearStart;

    @Column(nullable = false)
    private int semesterNumber;

    @Column(nullable = false)
    private int credits;

    @Column(nullable = false)
    private LocalDateTime registeredAt;

    private LocalDateTime droppedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentStatus status;

    @Column(name = "component_score_1")
    private BigDecimal componentScore1;

    @Column(name = "component_score_2")
    private BigDecimal componentScore2;

    private BigDecimal processScore;

    @Builder.Default
    @Column(nullable = false, length = 500)
    private String note = "";

    private BigDecimal finalExamScore;

    @Column(name = "course_score_grade_10")
    private BigDecimal courseScore10;

    @Column(name = "course_score_grade_letter")
    private String courseScoreLetter;

    @Column(name = "course_score_grade_4")
    private BigDecimal courseScore4;

    public void drop(LocalDateTime droppedAt) {
        this.status = EnrollmentStatus.DROPPED;
        this.droppedAt = droppedAt;
        setUpdatedAt(droppedAt);
    }

    /** Chỉ thay đổi điểm quá trình và ghi chú; trạng thái lượt học và điểm thi/tổng kết giữ nguyên. */
    public void updateProcessScores(
            BigDecimal componentScore1,
            BigDecimal componentScore2,
            BigDecimal processScore,
            String note,
            LocalDateTime updatedAt) {
        this.componentScore1 = componentScore1;
        this.componentScore2 = componentScore2;
        this.processScore = processScore;
        this.note = note;
        setUpdatedAt(updatedAt);
    }

    // TODO(FINAL_EXAM_GRADING): UC nhập điểm thi sẽ tính điểm học phần khi đủ quá trình và điểm thi:
    //  0,3*quá trình+0,7*thi, HALF_UP một chữ số; quy đổi hệ điểm và cập nhật COMPLETED/FAILED
    //  theo quy chế đã chốt. Hiện nhập điểm quá trình không thay điểm thi, tổng kết hoặc trạng thái lượt học.
    //  Lớp hết lịch giảng dạy không tự tạo điểm hoặc xác nhận sinh viên đã đạt môn.
}
