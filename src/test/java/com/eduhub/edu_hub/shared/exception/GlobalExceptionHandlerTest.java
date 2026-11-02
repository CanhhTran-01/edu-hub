package com.eduhub.edu_hub.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
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
