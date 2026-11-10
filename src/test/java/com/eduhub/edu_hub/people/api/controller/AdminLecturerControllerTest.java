package com.eduhub.edu_hub.people.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduhub.edu_hub.curriculum.application.exception.TeachingSubjectException;
import com.eduhub.edu_hub.people.api.dto.request.CreateLecturerRequest;
import com.eduhub.edu_hub.people.api.dto.request.LecturerSearchRequest;
import com.eduhub.edu_hub.people.api.dto.response.CreateLecturerResponse;
import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.api.exception.LecturerCreationExceptionHandler;
import com.eduhub.edu_hub.people.application.exception.ProfileCreationException;
import com.eduhub.edu_hub.people.application.exception.ProfileSearchException;
import com.eduhub.edu_hub.people.application.service.LecturerApplicationService;
import com.eduhub.edu_hub.people.application.service.LecturerCreationService;
import com.eduhub.edu_hub.people.domain.enums.LecturerStatus;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminLecturerControllerTest {
    private final LecturerApplicationService lecturerApplicationService = mock(LecturerApplicationService.class);
    private final LecturerCreationService lecturerCreationService = mock(LecturerCreationService.class);
    private final Jwt authenticatedJwt = Jwt.withTokenValue("test-token")
            .header("alg", "HS256")
            .subject("academic-admin")
            .build();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                    new AdminLecturerController(lecturerApplicationService, lecturerCreationService))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new LecturerCreationExceptionHandler(), new GlobalExceptionHandler())
            .build();

    @BeforeEach
    void authenticateActor() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(authenticatedJwt));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPassesPrincipalAndNewFieldsAndNormalizesEmailBeforeValidation() throws Exception {
        when(lecturerCreationService.createLecturer(eq(authenticatedJwt), any()))
                .thenReturn(new CreateLecturerResponse(
                        LecturerResponse.builder()
                                .id(1L)
                                .lecturerCode("CNTT26001")
                                .recruitmentYear(2026)
                                .build(),
                        List.of(),
                        "SENT",
                        null));
        mockMvc.perform(post("/api/v1/lecturers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreationJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lecturer.lecturerCode").value("CNTT26001"));
        var requestCaptor = ArgumentCaptor.forClass(CreateLecturerRequest.class);
        verify(lecturerCreationService).createLecturer(eq(authenticatedJwt), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getEmail()).isEqualTo("an@example.test");
        assertThat(requestCaptor.getValue().getRecruitmentYear()).isEqualTo(2026);
        assertThat(requestCaptor.getValue().getTeachingSubjectIds()).containsExactly(10L);
    }

    @Test
    void createRequiresRecruitmentYearAndAtLeastOneSubject() throws Exception {
        mockMvc.perform(post("/api/v1/lecturers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreationJson().replace("\"recruitmentYear\":2026,", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("recruitmentYear"));
        mockMvc.perform(post("/api/v1/lecturers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreationJson().replace("[10]", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("teachingSubjectIds"));
        mockMvc.perform(post("/api/v1/lecturers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreationJson().replace("[10]", "[null]")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(lecturerCreationService);
    }

    @Test
    void reportsDuplicateProfileAndUnavailableSubjectErrorsOnTheirFields() throws Exception {
        when(lecturerCreationService.createLecturer(eq(authenticatedJwt), any()))
                .thenThrow(new ProfileCreationException(
                        ErrorCode.DUPLICATE_LECTURER_EMAIL, "email", "Email giảng viên đã tồn tại"))
                .thenThrow(new TeachingSubjectException(ErrorCode.SUBJECT_NOT_TEACHABLE));
        mockMvc.perform(post("/api/v1/lecturers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreationJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4117))
                .andExpect(jsonPath("$.errors[0].field").value("email"));
        mockMvc.perform(post("/api/v1/lecturers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreationJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4212))
                .andExpect(jsonPath("$.errors[0].field").value("teachingSubjectIds"));
    }

    @Test
    void noLongerExposesResendEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/lecturers/1/account-notification/resend")).andExpect(status().isNotFound());
        verifyNoInteractions(lecturerCreationService);
    }

    private String validCreationJson() {
        return """
                {"fullName":"Nguyễn Văn An","dateOfBirth":"1980-01-01","email":" AN@EXAMPLE.TEST ",
                 "facultyId":1,"recruitmentYear":2026,"teachingSubjectIds":[10],"reason":"Bổ sung giảng viên"}
                """;
    }

    @ParameterizedTest
    @EnumSource(LecturerStatus.class)
    void searchBindsStatusPaginationAndOtherFiltersIntoTheRequest(LecturerStatus lecturerStatus) throws Exception {
        when(lecturerApplicationService.search(any()))
                .thenReturn(PageResponse.<LecturerResponse>builder()
                        .content(List.of())
                        .pageNumber(2)
                        .pageSize(5)
                        .isLast(true)
                        .build());

        mockMvc.perform(get("/api/v1/lecturers")
                        .param("keyword", "An")
                        .param("lecturerCode", "GV")
                        .param("fullName", "Nguyen")
                        .param("facultyId", "2")
                        .param("status", lecturerStatus.name())
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Không tìm thấy kết quả"))
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.pageNumber").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(5))
                .andExpect(jsonPath("$.data.last").value(true));

        var requestCaptor = ArgumentCaptor.forClass(LecturerSearchRequest.class);
        verify(lecturerApplicationService).search(requestCaptor.capture());
        LecturerSearchRequest request = requestCaptor.getValue();
        assertThat(request.getKeyword()).isEqualTo("An");
        assertThat(request.getLecturerCode()).isEqualTo("GV");
        assertThat(request.getFullName()).isEqualTo("Nguyen");
        assertThat(request.getFacultyId()).isEqualTo(2L);
        assertThat(request.getStatus()).isEqualTo(lecturerStatus.name());
        assertThat(request.getPage()).isEqualTo(2);
        assertThat(request.getSize()).isEqualTo(5);
        verifyNoInteractions(lecturerCreationService);
    }

    @Test
    void searchUsesDefaultPaginationAndOmitsMessageForMatchingLecturers() throws Exception {
        when(lecturerApplicationService.search(any()))
                .thenReturn(PageResponse.<LecturerResponse>builder()
                        .content(List.of(LecturerResponse.builder().id(1L).build()))
                        .pageSize(20)
                        .totalElements(1)
                        .totalPages(1)
                        .isLast(true)
                        .build());

        mockMvc.perform(get("/api/v1/lecturers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].id").value(1));

        var requestCaptor = ArgumentCaptor.forClass(LecturerSearchRequest.class);
        verify(lecturerApplicationService).search(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getPage()).isZero();
        assertThat(requestCaptor.getValue().getSize()).isEqualTo(20);
        assertThat(requestCaptor.getValue().getStatus()).isNull();
    }

    @Test
    void searchReturnsTheExistingValidationContractForServiceRejections() throws Exception {
        when(lecturerApplicationService.search(any())).thenThrow(new ProfileSearchException());

        mockMvc.perform(get("/api/v1/lecturers").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.path").value("/api/v1/lecturers"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size"})
    void searchRejectsMalformedPaginationBeforeCallingService(String parameterName) throws Exception {
        mockMvc.perform(get("/api/v1/lecturers").param(parameterName, "not-an-integer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));

        verifyNoInteractions(lecturerApplicationService, lecturerCreationService);
    }
}
