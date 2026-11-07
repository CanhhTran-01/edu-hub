package com.eduhub.edu_hub.admission.domain.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AdmissionBalancePolicy {
    public BigDecimal average(List<BigDecimal> scores) {
        return scores.isEmpty()
                ? null
                : scores.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(scores.size()), 12, RoundingMode.HALF_UP);
    }

    public BigDecimal difference(List<BigDecimal> averages) {
        return averages.isEmpty()
                ? BigDecimal.ZERO
                : Collections.max(averages).subtract(Collections.min(averages)).setScale(2, RoundingMode.HALF_UP);
    }
}
