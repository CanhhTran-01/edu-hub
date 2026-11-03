package com.eduhub.edu_hub.people.application.admission;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdmissionCandidate {
    private int rowNumber;
    private String applicationCode;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String email;
    private BigDecimal entranceScore;
    private String majorCode;
    private String cohortCode;
    private String trainingType;
    private Long assignedClassId;
}
