package com.eduhub.edu_hub.curriculum.api.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.curriculum.api.dto.response.StudentCurriculumResponse;
import com.eduhub.edu_hub.curriculum.api.dto.response.StudentCurriculumResponse.PlannedSemesterResponse;
import com.eduhub.edu_hub.curriculum.api.exception.CurriculumExceptionHandler;
import com.eduhub.edu_hub.curriculum.application.exception.StudentCurriculumException;
import com.eduhub.edu_hub.curriculum.application.service.StudentCurriculumQueryService;
import com.eduhub.edu_hub.shared.exception.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StudentCurriculumControllerTest {
    StudentCurriculumQueryService service = mock(StudentCurriculumQueryService.class);
    Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("student")
            .claim("token_use", "ACCESS")
            .build();
    MockMvc mvc;

    @BeforeEach
    void setup() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(principal));
        mvc = MockMvcBuilders.standaloneSetup(new StudentCurriculumController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new CurriculumExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsRoadmapFromPrincipalAndIgnoresClientTargetIds() throws Exception {
        when(service.findMyCurriculum(principal))
                .thenReturn(new StudentCurriculumResponse(
                        4L,
                        "CT",
                        "Chương trình",
                        2L,
                        "Ngành",
                        3L,
                        "Khóa",
                        "CHINH_QUY",
                        1,
                        125,
                        150,
                        List.of(new PlannedSemesterResponse(1, List.of()))));
        mvc.perform(get("/api/v1/students/me/curriculum")
                        .param("studentId", "999")
                        .param("curriculumId", "888"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.curriculumId").value(4))
                .andExpect(jsonPath("$.data.semesters[0].semesterNumber").value(1))
                .andExpect(jsonPath("$.data.semesters[0].subjects").isEmpty());
        verify(service).findMyCurriculum(principal);
    }

    @Test
    void returnsSpecificNotFoundWhenCurriculumIsNotAssigned() throws Exception {
        when(service.findMyCurriculum(principal))
                .thenThrow(new StudentCurriculumException(ErrorCode.CURRICULUM_NOT_FOUND));
        mvc.perform(get("/api/v1/students/me/curriculum"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4202));
    }
}
