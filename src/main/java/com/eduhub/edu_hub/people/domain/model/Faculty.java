package com.eduhub.edu_hub.people.domain.model;

import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "faculties")
@Getter
@Setter
public class Faculty extends BaseEntity {
    @Column(name = "faculty_name", nullable = false)
    private String facultyName;

    @Column(nullable = false)
    private boolean active = true;
}
