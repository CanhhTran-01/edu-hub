package com.eduhub.edu_hub.enrollment.api.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.enrollment.api.exception.EnrollmentExceptionHandler;
import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.enrollment.application.service.EnrollmentApplicationService;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EnrollmentControllerTest {
    private final EnrollmentApplicationService service = mock(EnrollmentApplicationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new EnrollmentController(service))
                .setControllerAdvice(new EnrollmentExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new org.springframework.web.method.support.HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                        return parameter.getParameterType() == org.springframework.security.oauth2.jwt.Jwt.class;
                    }

                    @Override
                    public Object resolveArgument(
                            org.springframework.core.MethodParameter parameter,
                            org.springframework.web.method.support.ModelAndViewContainer container,
                            org.springframework.web.context.request.NativeWebRequest request,
                            org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                        return org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token")
                                .header("alg", "HS256")
                                .subject("student")
                                .build();
                    }
                })
                .build();
    }

    @Test
    void validatesTargetClassIdBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/students/me/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleClassId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(service);
    }

    @Test
    void keepsBusinessDetailsForMissingPrerequisites() throws Exception {
        when(service.register(any(), any()))
                .thenThrow(new EnrollmentException(
                        ErrorCode.PREREQUISITE_NOT_MET,
                        List.of(new FieldErrorDetail("prerequisites[4]", "Chưa đạt CS4"))));
        mvc.perform(post("/api/v1/students/me/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleClassId\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4403))
                .andExpect(jsonPath("$.errors[0].field").value("prerequisites[4]"));
    }
}
