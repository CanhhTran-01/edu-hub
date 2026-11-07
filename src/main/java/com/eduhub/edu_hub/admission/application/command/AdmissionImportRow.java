package com.eduhub.edu_hub.admission.application.command;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.*;

@Data
@Builder
public class AdmissionImportRow {
    private int rowNumber;
    private String applicationCode;
    private String citizenId;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String email;
    private String phone;
    private BigDecimal entranceScore;

    @Builder.Default
    private AdmissionApplicantStatus state = AdmissionApplicantStatus.VALID;

    private String errorMessage;
}
