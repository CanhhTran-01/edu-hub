package com.eduhub.edu_hub.admission.infrastructure.spreadsheet;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.*;

/** Dữ liệu một dòng đã parse, chưa phải entity và chưa được đối chiếu danh tính trong hệ thống. */
@Data
@Builder
public class AdmissionSpreadsheetApplicant {
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
