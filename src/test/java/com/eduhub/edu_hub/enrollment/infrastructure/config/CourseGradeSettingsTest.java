package com.eduhub.edu_hub.enrollment.infrastructure.config;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.enrollment.domain.policy.CourseGradePolicy;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class CourseGradeSettingsTest {
    @Test
    void bindsExplicitGradeBandsWithoutStartingASpringContext() {
        var configuration = new MapConfigurationPropertySource(Map.of(
                "enrollment.grading.grade-scale[0].minimum-score", "0",
                "enrollment.grading.grade-scale[0].letter", "F",
                "enrollment.grading.grade-scale[0].grade-point", "0",
                "enrollment.grading.grade-scale[1].minimum-score", "4",
                "enrollment.grading.grade-scale[1].letter", "D",
                "enrollment.grading.grade-scale[1].grade-point", "1"));
        var settings = new Binder(configuration)
                .bind("enrollment.grading", Bindable.of(CourseGradeSettings.class))
                .get();
        var bands = CourseGradePolicy.requireValidScale(settings.getGradeScale());
        assertThat(bands).hasSize(2);
        assertThat(bands.getLast().minimumScore()).isEqualByComparingTo("4");
        assertThat(new CourseGradeSettings().getGradeScale()).isEmpty();
        assertThat(new CourseGradeSettings().getGradeCorrectionWindow()).isEqualTo(Duration.ofDays(14));
    }

    @ParameterizedTest
    @CsvSource({"30d,PT720H", "12h,PT12H", "45m,PT45M", "0,PT0S", "3,PT72H"})
    void bindsTheConfiguredWindowWithExplicitUnitsOrDaysByDefault(String configuredWindow, String expectedWindow) {
        var configuration = new MapConfigurationPropertySource(
                Map.of("enrollment.grading.grade-correction-window", configuredWindow));
        var settings = new Binder(configuration)
                .bind("enrollment.grading", Bindable.of(CourseGradeSettings.class))
                .get();
        assertThat(settings.getGradeCorrectionWindow()).isEqualTo(Duration.parse(expectedWindow));
    }
}
