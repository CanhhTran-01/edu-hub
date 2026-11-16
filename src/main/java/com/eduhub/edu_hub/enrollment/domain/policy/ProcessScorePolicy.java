package com.eduhub.edu_hub.enrollment.domain.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Quy tắc điểm quá trình thuần; không suy kết quả đạt/rớt từ điểm thành phần. */
public final class ProcessScorePolicy {
    private static final BigDecimal MAX_SCORE = BigDecimal.TEN;
    private static final BigDecimal COMPONENT_ONE_WEIGHT = new BigDecimal("0.3");
    private static final BigDecimal COMPONENT_TWO_WEIGHT = new BigDecimal("0.7");
    public static final int MAX_NOTE_LENGTH = 500;

    private ProcessScorePolicy() {}

    public static boolean isValidComponentScore(BigDecimal score) {
        return score == null
                || (score.signum() >= 0
                        && score.compareTo(MAX_SCORE) <= 0
                        && score.stripTrailingZeros().scale() <= 3);
    }

    public static BigDecimal calculateProcessScore(BigDecimal componentScore1, BigDecimal componentScore2) {
        if (componentScore1 == null || componentScore2 == null) return null;
        return componentScore1
                .multiply(COMPONENT_ONE_WEIGHT)
                .add(componentScore2.multiply(COMPONENT_TWO_WEIGHT))
                .setScale(1, RoundingMode.HALF_UP);
    }

    public static String normalizeNote(String note) {
        return note == null ? "" : note.strip();
    }

    public static boolean hasSameScore(BigDecimal firstScore, BigDecimal secondScore) {
        return firstScore == null ? secondScore == null : secondScore != null && firstScore.compareTo(secondScore) == 0;
    }
}
