package com.eduhub.edu_hub.scheduling.domain.model;

import com.eduhub.edu_hub.scheduling.domain.enums.TimetablePlanState;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/** Chỉ giữ bản chụp đầu vào và phương án tốt nhất; không lưu mọi bước tìm kiếm của solver. */
@Entity
@Table(name = "timetable_plans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimetablePlan extends BaseEntity {
    @Column(name = "semester_id", nullable = false)
    private Long semesterId;

    @Version
    private long revision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimetablePlanState state;

    @Column(name = "input_fingerprint", nullable = false, length = 64)
    private String inputFingerprint;

    @Column(name = "problem_json", nullable = false, columnDefinition = "text")
    private String problemJson; // Bản chụp bài toán đầu vào dưới dạng JSON

    @Column(name = "allocation_json", columnDefinition = "text")
    private String allocationJson; // Phương án tốt nhất

    @Column(name = "diagnostics_json", nullable = false, columnDefinition = "text")
    private String diagnosticsJson; // Thông tin chẩn đoán, có thể chứa danh sách violations

    @Column(name = "hard_penalty")
    private Long hardPenalty;

    @Column(name = "soft_penalty")
    private Long softPenalty;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;
}
