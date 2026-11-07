package com.eduhub.edu_hub.admission.domain.model;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionNotificationKind;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionNotificationStatus;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "admission_notifications")
@Builder
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AdmissionNotification extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admission_id")
    private StudentAdmission admission;

    @Column(name = "student_id")
    private Long studentId;

    private String recipient;

    @Enumerated(EnumType.STRING)
    private AdmissionNotificationKind kind;

    @Enumerated(EnumType.STRING)
    private AdmissionNotificationStatus state;

    // Initial failure plus up to 3 failed scheduled retries; terminal at 4, never reset.
    private int failureCount;
    private LocalDateTime nextAttemptAt;
    private String lastError;
}
