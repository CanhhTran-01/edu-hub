package com.eduhub.edu_hub.enrollment.application.service;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.enrollment.api.dto.request.MinimumExamScoreRequest;
import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.enrollment.domain.enums.MinimumExamScoreStatus;
import com.eduhub.edu_hub.enrollment.domain.policy.CourseGradePolicy.GradeBand;
import com.eduhub.edu_hub.enrollment.infrastructure.config.CourseGradeSettings;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MinimumExamScoreServiceTest {
    CourseGradeSettings settings = new CourseGradeSettings();
    Clock clock = Clock.fixed(Instant.parse("2026-11-27T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    MinimumExamScoreService service = new MinimumExamScoreService(settings, clock);

    @BeforeEach
    void prepare() {
        settings.setGradeScale(List.of(
                band("0", "F", "0"),
                band("4", "D", "1"),
                band("7", "B", "3"),
                band("8.5", "A", "4"),
                band("9", "A+", "4")));
    }

    @Test
    void usesUserInputsWithoutRequiringATargetProfileSemesterOrEnrollment() {
        var response = service.solveMinimumExamScore(
                new MinimumExamScoreRequest("Giải tích 1", decimal("8"), decimal("7"), " a "));
        assertThat(response.status()).isEqualTo(MinimumExamScoreStatus.SOLVED);
        assertThat(response.minimumExamScore()).isEqualByComparingTo("8.943");
        assertThat(response.targetGradeLetter()).isEqualTo("A");
        assertThat(response.calculatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 27, 7, 0));
        assertThat(response.gradeScale().bands()).hasSize(5);
    }

    @Test
    void missingValuesAndUnfilledPlaceholdersAskInsteadOfBecomingZero() {
        for (var request : List.of(
                new MinimumExamScoreRequest("...", decimal("8"), decimal("7"), "A"),
                new MinimumExamScoreRequest("Giải tích 1", null, decimal("7"), "A"),
                new MinimumExamScoreRequest("Giải tích 1", decimal("8"), decimal("7"), "…"))) {
            var response = service.solveMinimumExamScore(request);
            assertThat(response.status()).isEqualTo(MinimumExamScoreStatus.NEEDS_CLARIFICATION);
            assertThat(response.minimumExamScore()).isNull();
        }
    }

    @Test
    void invalidScoresAndUnknownLettersAreExplainedAsClarifications() {
        assertThat(service.solveMinimumExamScore(
                                new MinimumExamScoreRequest("Môn X", decimal("10.001"), decimal("7"), "A"))
                        .message())
                .contains("từ 0 đến 10");
        assertThat(service.solveMinimumExamScore(new MinimumExamScoreRequest("Môn X", decimal("8"), decimal("7"), "C"))
                        .status())
                .isEqualTo(MinimumExamScoreStatus.NEEDS_CLARIFICATION);
    }

    @Test
    void usesTheCurrentScaleForEveryCalculationAndDoesNotInventAFallbackScale() {
        settings.setGradeScale(List.of(band("0", "F", "0"), band("4", "D", "1"), band("8", "A", "4")));
        assertThat(service.solveMinimumExamScore(new MinimumExamScoreRequest("Môn X", decimal("8"), decimal("7"), "A"))
                        .minimumExamScore())
                .isEqualByComparingTo("8.229");
        settings.setGradeScale(List.of());
        assertThatThrownBy(() -> service.solveMinimumExamScore(
                        new MinimumExamScoreRequest("Môn X", decimal("8"), decimal("7"), "A")))
                .isInstanceOfSatisfying(EnrollmentException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(ErrorCode.COURSE_GRADE_CONFIGURATION_INVALID));
    }

    private GradeBand band(String score, String letter, String points) {
        return new GradeBand(decimal(score), letter, decimal(points));
    }

    private BigDecimal decimal(String number) {
        return new BigDecimal(number);
    }
}
