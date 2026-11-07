package com.eduhub.edu_hub.admission.application.mapper;

import com.eduhub.edu_hub.admission.application.result.AdmissionResults.ScenarioResult;
import com.eduhub.edu_hub.admission.domain.model.ClassDivisionScenario;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AdmissionResultMapper {
    ScenarioResult scenario(ClassDivisionScenario source);
}
