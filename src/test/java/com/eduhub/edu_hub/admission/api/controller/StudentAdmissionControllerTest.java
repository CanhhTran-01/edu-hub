package com.eduhub.edu_hub.admission.api.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.admission.api.exception.StudentAdmissionExceptionHandler;
import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.PlanCommand;
import com.eduhub.edu_hub.admission.application.exception.AdmissionImportScopeException;
import com.eduhub.edu_hub.admission.application.service.AdmissionAllocationService;
import com.eduhub.edu_hub.admission.application.service.AdmissionConfirmationService;
import com.eduhub.edu_hub.admission.application.service.AdmissionImportService;
import com.eduhub.edu_hub.admission.application.service.AdmissionNotificationQueryService;
import com.eduhub.edu_hub.admission.application.service.AdmissionPlanningService;
import com.eduhub.edu_hub.admission.infrastructure.config.AdmissionSettings;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StudentAdmissionControllerTest {
    private final AdmissionPlanningService planning = mock(AdmissionPlanningService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new StudentAdmissionController(
                        org.mapstruct.factory.Mappers.getMapper(
                                com.eduhub.edu_hub.admission.api.mapper.AdmissionApiMapper.class),
                        planning,
                        mock(AdmissionImportService.class),
                        mock(AdmissionAllocationService.class),
                        mock(AdmissionConfirmationService.class),
                        mock(AdmissionNotificationQueryService.class),
                        new AdmissionSettings()))
                .setControllerAdvice(new StudentAdmissionExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer ignored", "Basic ignored"})
    void configurationDoesNotInspectAuthorization(String authorization) throws Exception {
        var request = get("/api/v1/student-admissions/configuration");
        if (!authorization.isEmpty()) request.header("Authorization", authorization);
        mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reserve").value(15));
    }

    @Test
    void createsPlanWithoutControllerAuthorizationChecks() throws Exception {
        mvc.perform(post("/api/v1/student-admissions/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"majorId\":1,\"cohortId\":8,\"totalStudents\":120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(planning).create(new PlanCommand(1L, 8L, 120, null, null, null));
    }

    @Test
    void rejectsNegativePopulation() throws Exception {
        mvc.perform(post("/api/v1/student-admissions/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"majorId\":1,\"cohortId\":1,\"totalStudents\":-1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(planning);
    }

    @Test
    void noLongerExposesManualNotificationRetry() throws Exception {
        mvc.perform(post("/api/v1/student-admissions/notifications/1/retry")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsMissingUploadFileAsBadRequest() throws Exception {
        mvc.perform(multipart("/api/v1/student-admissions/plans/import")
                        .param("majorId", "1")
                        .param("cohortId", "1"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(planning);
    }

    @Test
    void requiresBothMajorAndCohortBeforeDownloadingTemplate() throws Exception {
        mvc.perform(get("/api/v1/student-admissions/template")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/student-admissions/template").param("majorId", "1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/student-admissions/template").param("cohortId", "8"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(planning);
    }

    @Test
    void downloadsTemplateForTheSelectedMajorAndCohort() throws Exception {
        byte[] template = {1, 2, 3};
        when(planning.template(1L, 8L)).thenReturn(template);
        mvc.perform(get("/api/v1/student-admissions/template")
                        .param("majorId", "1")
                        .param("cohortId", "8"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=admission-template.xlsx"))
                .andExpect(content().bytes(template));
        verify(planning).template(1L, 8L);
    }

    @Test
    void requiresBothMajorAndCohortWhenCreatingAPlanFromFile() throws Exception {
        var file = new MockMultipartFile("file", "students.xlsx", "application/octet-stream", new byte[] {1});
        mvc.perform(multipart("/api/v1/student-admissions/plans/import")
                        .file(file)
                        .param("majorId", "1"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/student-admissions/plans/import")
                        .file(file)
                        .param("cohortId", "8"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(planning);
    }

    @Test
    void returnsTheMismatchedFileAndSelectedCodesInTheImportError() throws Exception {
        when(planning.createWithFile(any(), any()))
                .thenThrow(new AdmissionImportScopeException(List.of(
                        new FieldErrorDetail("majorCode", "Ngành trong file: ATTT; ngành đã chọn: CNTT"),
                        new FieldErrorDetail("cohortCode", "Khóa trong file: CT9; khóa đã chọn: CT8"))));
        mvc.perform(multipart("/api/v1/student-admissions/plans/import")
                        .file(new MockMultipartFile(
                                "file", "students.xlsx", "application/octet-stream", new byte[] {1}))
                        .param("majorId", "1")
                        .param("cohortId", "8"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ADMISSION_IMPORT_SCOPE_MISMATCH.getCode()))
                .andExpect(jsonPath("$.errors[0].field").value("majorCode"))
                .andExpect(jsonPath("$.errors[0].message").value("Ngành trong file: ATTT; ngành đã chọn: CNTT"))
                .andExpect(jsonPath("$.errors[1].field").value("cohortCode"))
                .andExpect(jsonPath("$.errors[1].message").value("Khóa trong file: CT9; khóa đã chọn: CT8"))
                .andExpect(jsonPath("$.path").value("/api/v1/student-admissions/plans/import"));
    }
}
