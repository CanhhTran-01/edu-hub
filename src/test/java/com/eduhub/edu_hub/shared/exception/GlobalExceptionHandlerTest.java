package com.eduhub.edu_hub.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.config.TimeConfig;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-18T17:30:00Z"),
            new TimeConfig().applicationClock().getZone());
    private final ApiResponseFactory apiResponseFactory = new ApiResponseFactory(CLOCK);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(apiResponseFactory);
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/test");

    @Test
    void malformedJsonReturnsBadRequestInsteadOfServerError() {
        var exception = new HttpMessageNotReadableException("Invalid JSON", new MockHttpInputMessage(new byte[0]));

        var response = handler.handlingHttpMessageNotReadableException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/test");
    }
}
