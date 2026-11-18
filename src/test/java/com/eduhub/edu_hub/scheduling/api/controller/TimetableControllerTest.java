package com.eduhub.edu_hub.scheduling.api.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.scheduling.api.dto.request.CreateTimetablePlanRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.TimetablePlanResponse;
import com.eduhub.edu_hub.scheduling.api.exception.TimetableExceptionHandler;
import com.eduhub.edu_hub.scheduling.application.exception.TimetableException;
import com.eduhub.edu_hub.scheduling.application.service.TimetablePlanApplicationService;
import com.eduhub.edu_hub.scheduling.domain.enums.TimetablePlanState;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;

class TimetableControllerTest {
    private final TimetablePlanApplicationService service = mock(TimetablePlanApplicationService.class);
    private final Jwt principal =
            Jwt.withTokenValue("token").header("alg", "HS256").subject("admin").build();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TimetableController(service))
                .setControllerAdvice(new GlobalExceptionHandler(), new TimetableExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == Jwt.class;
                    }

                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer container,
                            NativeWebRequest request,
                            WebDataBinderFactory binderFactory) {
                        return principal;
                    }
                })
                .build();
    }

    @Test
    void acceptsBackgroundRequestAndUsesAuthenticatedPrincipal() throws Exception {
        when(service.createPlan(eq(principal), eq(7L), any()))
                .thenReturn(new TimetablePlanResponse(
                        9L, 7L, 0, TimetablePlanState.QUEUED, null, null, List.of(), null, null, null, false));
        mvc.perform(post("/api/v1/semesters/7/timetable-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.state").value("QUEUED"))
                .andExpect(jsonPath("$.data.canApply").value(false));
        var request = ArgumentCaptor.forClass(CreateTimetablePlanRequest.class);
        verify(service).createPlan(eq(principal), eq(7L), request.capture());
        assertThat(request.getValue().getPinnedModuleClassIds()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"pinnedModuleClassIds\":null}",
                "{\"pinnedModuleClassIds\":[null]}",
                "{\"pinnedModuleClassIds\":[0]}"
            })
    void rejectsInvalidPinnedClassIds(String json) throws Exception {
        mvc.perform(post("/api/v1/semesters/7/timetable-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(service);
    }

    @Test
    void requiresRevisionWhenApplying() throws Exception {
        mvc.perform(post("/api/v1/timetable-plans/9/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(service);
    }

    @Test
    void keepsDetailedInputErrorsInEnvelope() throws Exception {
        when(service.createPlan(eq(principal), eq(7L), any()))
                .thenThrow(new TimetableException(
                        ErrorCode.TIMETABLE_INVALID_INPUT,
                        List.of(new FieldErrorDetail("subjects[1].totalTeachingPeriods", "Cần bổ sung tổng tiết"))));
        mvc.perform(post("/api/v1/semesters/7/timetable-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4332))
                .andExpect(jsonPath("$.errors[0].field").value("subjects[1].totalTeachingPeriods"));
    }
}
