package com.eduhub.edu_hub.aiassistant.api.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GraduationScenarioResponse {
    private BigDecimal targetGpa;
    private BigDecimal currentGpa;
    private BigDecimal requiredGpa;
    private BigDecimal maxPossibleGpa;
    private String viabilityStatus;
    private Object roadmap; // JSON
}
