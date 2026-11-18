package com.eduhub.edu_hub.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduhub.edu_hub.people.api.dto.request.UpdateStudentProfileRequest;
import jakarta.validation.Validation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TimeConfigTest {
    private final TimeConfig configuration = new TimeConfig();

    @Test
    void applicationClockUsesVietnamZoneWithoutChangingJvmDefault() {
        TimeZone defaultTimeZone = TimeZone.getDefault();

        Clock clock = configuration.applicationClock();

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(TimeZone.getDefault()).isEqualTo(defaultTimeZone);
    }

    @Test
    void auditProviderReadsEachInstantAndKeepsUtcAcrossVietnamMidnight() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse("2026-11-18T17:30:00Z"), Instant.parse("2026-11-18T17:31:00Z"));
        var provider = configuration.applicationDateTimeProvider(clock);

        assertThat(provider.getNow()).contains(LocalDateTime.parse("2026-11-18T17:30:00"));
        assertThat(provider.getNow()).contains(LocalDateTime.parse("2026-11-18T17:31:00"));
    }

    @ParameterizedTest
    @CsvSource({"2026-11-17, true", "2026-11-18, true", "2026-11-19, false"})
    void pastValidationUsesVietnamDateAcrossUtcMidnight(String dateOfBirth, boolean valid) {
        Clock clock = Clock.fixed(
                Instant.parse("2026-11-18T17:30:00Z"),
                configuration.applicationClock().getZone());
        var validationConfiguration = Validation.byDefaultProvider().configure();
        configuration.validationClockCustomizer(clock).customize(validationConfiguration);

        try (var validatorFactory = validationConfiguration.buildValidatorFactory()) {
            var request = new UpdateStudentProfileRequest();
            request.setDateOfBirth(LocalDate.parse(dateOfBirth));

            assertThat(validatorFactory.getClockProvider().getClock().instant()).isEqualTo(clock.instant());
            assertThat(validatorFactory.getClockProvider().getClock()).isSameAs(clock);
            assertThat(validatorFactory.getClockProvider().getClock().getZone()).isEqualTo(TimeConfig.APPLICATION_ZONE);
            assertThat(validatorFactory.getValidator().validate(request).isEmpty())
                    .isEqualTo(valid);
        }
    }
}
