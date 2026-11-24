package com.eduhub.edu_hub.curriculum.domain.model;

import com.eduhub.edu_hub.curriculum.domain.enums.CatalogStatus;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "majors")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Major extends BaseEntity {
    @Column(name = "major_code", nullable = false, columnDefinition = "text")
    private String majorCode;

    @Column(name = "major_name", nullable = false, columnDefinition = "text")
    private String majorName;

    @Column(name = "faculty_id")
    private Long facultyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CatalogStatus status;
}
