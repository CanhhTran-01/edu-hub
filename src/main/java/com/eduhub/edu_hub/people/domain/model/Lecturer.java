package com.eduhub.edu_hub.people.domain.model;

import com.eduhub.edu_hub.people.domain.enums.LecturerStatus;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lecturers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Lecturer extends BaseEntity {
    // Hồ sơ cũ chưa biết năm tuyển dụng giữ null, không suy từ thời điểm nhập dữ liệu.
    @Column(name = "recruitment_year")
    private Integer recruitmentYear;

    @Column(name = "user_id", unique = true)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id")
    private Faculty faculty;

    @Column(name = "lecturer_code", nullable = false, unique = true)
    private String lecturerCode;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;

    private LocalDate dateOfBirth;
    private String citizenId;

    @Enumerated(EnumType.STRING)
    private LecturerStatus status;
}
