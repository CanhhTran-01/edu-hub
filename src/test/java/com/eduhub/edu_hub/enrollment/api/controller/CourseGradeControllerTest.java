package com.eduhub.edu_hub.enrollment.api.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.enrollment.api.dto.request.SearchCourseGradesRequest;
import com.eduhub.edu_hub.enrollment.api.dto.request.UpdateCourseGradeRequest;
import com.eduhub.edu_hub.enrollment.api.exception.EnrollmentExceptionHandler;
import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.enrollment.application.service.CourseGradeService;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CourseGradeControllerTest {
    private final ApiResponseFactory responses =
            new ApiResponseFactory(Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
    private final CourseGradeService service = mock(CourseGradeService.class);
    private final Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .claim("token_use", "ACCESS")
            .build();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(principal));
        mvc = MockMvcBuilders.standaloneSetup(new CourseGradeController(responses, service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new EnrollmentExceptionHandler(responses), new GlobalExceptionHandler(responses))
                .build();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passesTheAuthenticatedPrincipalAndTheSelectedAttemptWithoutStudentIdsInTheBody() throws Exception {
        mvc.perform(patch("/api/v1/enrollments/10/course-grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalExamScore\":0}"))
                .andExpect(status().isOk());
        var request = ArgumentCaptor.forClass(UpdateCourseGradeRequest.class);
        verify(service).updateCourseGrade(eq(principal), eq(10L), request.capture());
        assertThat(request.getValue().getFinalExamScore()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"studentId", "courseScore4", "gradeLockAt", "gradePublishedAt", "gradeLocked", "status"})
    void refusesReadOnlyFieldsEvenWhenNull(String field) throws Exception {
        mvc.perform(patch("/api/v1/enrollments/10/course-grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalExamScore\":8,\"" + field + "\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "\"8.5\"", "[]", "{}"})
    void refusesNonNumericExamScores(String score) throws Exception {
        mvc.perform(patch("/api/v1/enrollments/10/course-grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalExamScore\":" + score + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("finalExamScore"));
        verifyNoInteractions(service);
    }

    @Test
    void bindsSearchFiltersAndReturnsNoStore() throws Exception {
        mvc.perform(get("/api/v1/course-grades")
                        .param("keyword", "00001")
                        .param("semesterId", "7")
                        .param("subjectId", "20")
                        .param("moduleClassId", "100")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        var request = ArgumentCaptor.forClass(SearchCourseGradesRequest.class);
        verify(service).findCourseGrades(eq(principal), request.capture());
        assertThat(request.getValue().getKeyword()).isEqualTo("00001");
        assertThat(request.getValue().getSemesterId()).isEqualTo(7L);
        assertThat(request.getValue().getPage()).isEqualTo(1);
    }

    @Test
    void preservesImportRowErrorsAndHandlesMissingFilesWithTheCourseGradeError() throws Exception {
        when(service.importCourseGrades(eq(principal), eq(100L), any()))
                .thenThrow(new EnrollmentException(
                        ErrorCode.COURSE_GRADE_IMPORT_INVALID,
                        List.of(new FieldErrorDetail("rows[9].studentCode", "Không thuộc lớp"))));
        mvc.perform(multipart("/api/v1/module-classes/100/course-grades/import")
                        .file(new MockMultipartFile("file", "grades.xlsx", null, new byte[] {1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4506))
                .andExpect(jsonPath("$.errors[0].field").value("rows[9].studentCode"));
        mvc.perform(multipart("/api/v1/module-classes/100/course-grades/import"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4506));
    }

    @Test
    void downloadsTheClassTemplateAsXlsxBytes() throws Exception {
        when(service.createTemplate(principal, 100L)).thenReturn(new byte[] {1, 2});
        mvc.perform(get("/api/v1/module-classes/100/course-grades/template"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[] {1, 2}))
                .andExpect(header().string(
                                "Content-Disposition", "attachment; filename=module-class-100-course-grades.xlsx"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
