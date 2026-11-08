package com.eduhub.edu_hub.admission.domain.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AdmissionScoreBalancePolicy {

    // tính điểm trung bình của một danh sách điểm
    public BigDecimal calculateAverageScore(List<BigDecimal> scores) {
        return scores.isEmpty()
                ? null
                : scores.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(scores.size()), 12, RoundingMode.HALF_UP);
    }

    // tính độ chênh giữa max và min trong danh sách điểm
    public BigDecimal calculateAverageScoreGap(List<BigDecimal> classAverageScores) {
        return classAverageScores.isEmpty()
                ? BigDecimal.ZERO
                : Collections.max(classAverageScores)
                        .subtract(Collections.min(classAverageScores))
                        .setScale(2, RoundingMode.HALF_UP);
    }
}
