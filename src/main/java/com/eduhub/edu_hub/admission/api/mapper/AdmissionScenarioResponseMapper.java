package com.eduhub.edu_hub.admission.api.mapper;

import com.eduhub.edu_hub.admission.api.dto.response.AdmissionScenarioResponse;
import com.eduhub.edu_hub.admission.domain.model.ClassDivisionScenario;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AdmissionScenarioResponseMapper {
    AdmissionScenarioResponse toScenarioResponse(ClassDivisionScenario scenario);
}
