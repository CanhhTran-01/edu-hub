package com.eduhub.edu_hub.people.domain.model;

import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "admission_notifications")
@Getter
@Setter
public class AdmissionNotification extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admission_id")
    private StudentAdmission admission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    private String recipient;

    @Enumerated(EnumType.STRING)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    private State state;

    // Initial failure plus up to 3 failed scheduled retries; terminal at 4, never reset.
    private int failureCount;
    private LocalDateTime nextAttemptAt;
    private String lastError;

    public enum Kind {
        INITIAL_ACCOUNT,
        RE_ENROLLMENT
    }

    public enum State {
        PENDING,
        SENT,
        FAILED,
        SKIPPED
    }
}
