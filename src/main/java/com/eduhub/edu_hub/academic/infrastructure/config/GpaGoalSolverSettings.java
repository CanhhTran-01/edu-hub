package com.eduhub.edu_hub.academic.infrastructure.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Giới hạn tài nguyên tìm phương án, độc lập quy chế điểm và deadline chung của Agent. */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "academic.gpa-goal-solver")
public class GpaGoalSolverSettings {
    @Min(1)
    @Max(100000)
    private int maximumEvaluations = 10000;

    private Duration executionTimeout = Duration.ofSeconds(5);

    @AssertTrue(message = "Thời hạn tìm phương án GPA phải dương và không quá mười giây")
    public boolean isExecutionTimeoutValid() {
        return executionTimeout != null
                && !executionTimeout.isNegative()
                && !executionTimeout.isZero()
                && executionTimeout.compareTo(Duration.ofSeconds(10)) <= 0;
    }
}
