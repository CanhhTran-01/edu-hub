package com.eduhub.edu_hub.admission.application.mapper;

import com.eduhub.edu_hub.admission.application.result.AdmissionResults.ScenarioResult;
import com.eduhub.edu_hub.admission.domain.model.ClassDivisionScenario;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AdmissionScenarioMapper {
    ScenarioResult toScenarioResult(ClassDivisionScenario scenario);
}
