package com.eduhub.edu_hub.enrollment.infrastructure.config;

import com.eduhub.edu_hub.enrollment.domain.policy.CourseGradePolicy.GradeBand;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.stereotype.Component;

/** Không tự đặt thang quy đổi mặc định khi chưa có quy chế đào tạo chính thức. */
@Component
@ConfigurationProperties(prefix = "enrollment.grading")
@Getter
@Setter
public class CourseGradeSettings {
    @DurationUnit(ChronoUnit.DAYS)
    private Duration gradeCorrectionWindow = Duration.ofDays(14);

    private List<GradeBand> gradeScale = new ArrayList<>();
}
