package com.eduhub.edu_hub.academic.api.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class UpdateProcessScoreRequest {
    @NotNull
    private Long enrollmentId;

    private BigDecimal componentScore1;
    private BigDecimal componentScore2;
    private String note;
}
