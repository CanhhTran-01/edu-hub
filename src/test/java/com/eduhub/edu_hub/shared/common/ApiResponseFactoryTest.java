package com.eduhub.edu_hub.shared.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduhub.edu_hub.shared.config.TimeConfig;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ApiResponseFactoryTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-18T17:30:00Z"),
            new TimeConfig().applicationClock().getZone());
    private final ApiResponseFactory responseFactory = new ApiResponseFactory(CLOCK);

    @Test
    void allResponseKindsUseVietnamTimestampFromSharedClock() {
        var responses = List.of(
                responseFactory.success("student"),
                responseFactory.successWithMessage("Đã cập nhật"),
                responseFactory.error(ErrorCode.UNAUTHENTICATED, "/api/v1/students/me"),
                responseFactory.validationError(
                        List.of(new FieldErrorDetail("dateOfBirth", "Ngày sinh không hợp lệ")), "/api/v1/students"),
                responseFactory.<String>builder().success(true).data("custom").build());

        assertThat(responses).allSatisfy(response -> assertThat(response.getTimestamp())
                .isEqualTo(LocalDateTime.parse("2026-11-19T00:30:00")));
    }

    @Test
    void laterResponsesReadClockAgainInsteadOfKeepingFactoryCreationTime() {
        Clock clock = mock(Clock.class);
        when(clock.getZone()).thenReturn(TimeConfig.APPLICATION_ZONE);
        when(clock.instant()).thenReturn(Instant.parse("2026-11-18T17:30:00Z"), Instant.parse("2026-11-18T17:31:00Z"));
        var responseFactory = new ApiResponseFactory(clock);

        assertThat(responseFactory.success("first").getTimestamp())
                .isEqualTo(LocalDateTime.parse("2026-11-19T00:30:00"));
        assertThat(responseFactory.success("second").getTimestamp())
                .isEqualTo(LocalDateTime.parse("2026-11-19T00:31:00"));
    }

    @Test
    void keepsEnvelopeFieldsAndOmitsNullFields() {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var json = mapper.readTree(mapper.writeValueAsString(responseFactory.success("student")));

        assertThat(json.propertyNames()).containsExactlyInAnyOrder("success", "message", "data", "timestamp");
        assertThat(json.get("success").asBoolean()).isTrue();
        assertThat(json.get("message").asText()).isEqualTo("Success");
        assertThat(json.get("timestamp").asText()).isEqualTo("2026-11-19T00:30:00");
    }
}
