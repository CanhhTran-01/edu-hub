package com.eduhub.edu_hub.admission.application.mapper;

import com.eduhub.edu_hub.admission.application.command.AdmissionImportRow;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AdmissionApplicantMapper {
    AdmissionApplicant fromImportRow(AdmissionImportRow row);
}
