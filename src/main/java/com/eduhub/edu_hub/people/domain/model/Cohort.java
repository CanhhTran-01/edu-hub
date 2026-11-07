package com.eduhub.edu_hub.people.domain.model;

import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "cohorts")
@Getter
@Setter
public class Cohort extends BaseEntity {
    @Column(name = "cohort_code", nullable = false)
    private String cohortCode;

    @Column(name = "code_sequence", unique = true)
    private Integer codeSequence;

    @Column(name = "start_year")
    private Integer startYear;

    @Column(name = "end_year")
    private Integer endYear;
}
