package com.eduhub.edu_hub.enrollment.domain.policy;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProcessScorePolicyTest {
    @Test
    void requiresBothComponentsButTreatsZeroAsARealScore() {
        assertThat(ProcessScorePolicy.calculateProcessScore(null, BigDecimal.TEN))
                .isNull();
        assertThat(ProcessScorePolicy.calculateProcessScore(BigDecimal.ZERO, null))
                .isNull();
        assertThat(ProcessScorePolicy.calculateProcessScore(BigDecimal.ZERO, BigDecimal.TEN))
                .isEqualByComparingTo("7.0");
    }

    @Test
    void calculatesWithDecimalWeightsAndRoundsHalfUpAtOneDecimal() {
        assertThat(ProcessScorePolicy.calculateProcessScore(new BigDecimal("7.5"), new BigDecimal("8")))
                .isEqualTo(new BigDecimal("7.9"));
        assertThat(ProcessScorePolicy.calculateProcessScore(new BigDecimal("10"), new BigDecimal("10")))
                .isEqualTo(new BigDecimal("10.0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.001", "10.001", "8.1234"})
    void rejectsOutOfRangeOrExcessPrecisionInsteadOfRoundingInput(String score) {
        assertThat(ProcessScorePolicy.isValidComponentScore(new BigDecimal(score)))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "10", "9.123", "10.0000"})
    void acceptsBoundaryValuesAndInsignificantTrailingZeros(String score) {
        assertThat(ProcessScorePolicy.isValidComponentScore(new BigDecimal(score)))
                .isTrue();
    }
}
