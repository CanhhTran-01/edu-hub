package com.eduhub.edu_hub.people.domain.model;

import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** People sở hữu năng lực giảng dạy; môn học được kiểm tra qua facade, không giữ entity Curriculum. */
@Entity
@Table(name = "lecturer_teaching_subjects")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LecturerTeachingSubject extends BaseEntity {
    @Column(name = "lecturer_id", nullable = false)
    private Long lecturerId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;
}
