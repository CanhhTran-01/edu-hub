package com.eduhub.edu_hub.admission.application.mapper;

import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetApplicant;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AdmissionApplicantImportMapper {
    AdmissionApplicant fromSpreadsheetApplicant(AdmissionSpreadsheetApplicant spreadsheetApplicant);
}
