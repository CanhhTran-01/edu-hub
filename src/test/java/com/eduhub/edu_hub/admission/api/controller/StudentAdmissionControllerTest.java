package com.eduhub.edu_hub.admission.api.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.admission.api.dto.request.CreateAdmissionPlanFromFileRequest;
import com.eduhub.edu_hub.admission.api.dto.request.CreateAdmissionPlanRequest;
import com.eduhub.edu_hub.admission.api.dto.request.ImportAdmissionApplicantsRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionApplicantResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionCapacityResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanResponse;
import com.eduhub.edu_hub.admission.api.exception.AdmissionExceptionHandler;
import com.eduhub.edu_hub.admission.application.exception.AdmissionImportScopeException;
import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.application.service.AdmissionAllocationService;
import com.eduhub.edu_hub.admission.application.service.AdmissionConfirmationService;
import com.eduhub.edu_hub.admission.application.service.AdmissionImportService;
import com.eduhub.edu_hub.admission.application.service.AdmissionNotificationQueryService;
import com.eduhub.edu_hub.admission.application.service.AdmissionPlanningService;
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
    private final AdmissionImportService admissionImportService = mock(AdmissionImportService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        when(planning.getDefaultCapacityConfiguration()).thenReturn(new AdmissionCapacityResponse(40, 50, 15));
        mvc = MockMvcBuilders.standaloneSetup(new StudentAdmissionController(
                        planning,
                        admissionImportService,
                        mock(AdmissionAllocationService.class),
                        mock(AdmissionConfirmationService.class),
                        mock(AdmissionNotificationQueryService.class)))
                .setControllerAdvice(new AdmissionExceptionHandler(), new GlobalExceptionHandler())
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
        verify(planning).createPlan(new CreateAdmissionPlanRequest(1L, 8L, 120, null, null, null));
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
        when(planning.generateImportTemplate(1L, 8L)).thenReturn(template);
        mvc.perform(get("/api/v1/student-admissions/template")
                        .param("majorId", "1")
                        .param("cohortId", "8"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=admission-template.xlsx"))
                .andExpect(content().bytes(template));
        verify(planning).generateImportTemplate(1L, 8L);
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
        when(planning.createPlanFromFile(any()))
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

    @Test
    void preservesMultipartFieldNamesAndOptionalCapacityWhenCreatingAPlan() throws Exception {
        var file = new MockMultipartFile("file", "students.xlsx", "application/octet-stream", new byte[] {1});

        mvc.perform(multipart("/api/v1/student-admissions/plans/import")
                        .file(file)
                        .param("majorId", "1")
                        .param("cohortId", "8")
                        .param("minCapacity", "30")
                        .param("reserveCapacity", "10"))
                .andExpect(status().isOk());

        verify(planning).createPlanFromFile(new CreateAdmissionPlanFromFileRequest(1L, 8L, 30, null, 10, file));
    }

    @Test
    void passesTheExistingPlanRevisionAndFileTogetherToTheImportService() throws Exception {
        var file = new MockMultipartFile("file", "students.xlsx", "application/octet-stream", new byte[] {1});

        mvc.perform(multipart("/api/v1/student-admissions/plans/7/applicants")
                        .file(file)
                        .param("expectedRevision", "3"))
                .andExpect(status().isOk());

        verify(admissionImportService).importApplicants(7L, new ImportAdmissionApplicantsRequest(3, file));
    }

    @Test
    void preservesPlanAndApplicantJsonAfterRemovingTheIntermediateMapper() throws Exception {
        when(planning.getPlan(7L))
                .thenReturn(AdmissionPlanResponse.builder()
                        .id(7L)
                        .revision(3)
                        .majorId(1L)
                        .cohortId(8L)
                        .totalStudents(1)
                        .capacity(new AdmissionCapacityResponse(40, 50, 15))
                        .state("DRAFT")
                        .classes(List.of())
                        .scenarios(List.of())
                        .applicants(List.of(AdmissionApplicantResponse.builder()
                                .id(11L)
                                .rowNumber(6)
                                .citizenId("012345678901")
                                .state("VALID")
                                .reusingAccount(true)
                                .warnings(List.of())
                                .build()))
                        .warnings(List.of())
                        .build());

        mvc.perform(get("/api/v1/student-admissions/plans/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.revision").value(3))
                .andExpect(jsonPath("$.data.capacity.minimum").value(40))
                .andExpect(jsonPath("$.data.capacity.maximum").value(50))
                .andExpect(jsonPath("$.data.capacity.reserve").value(15))
                .andExpect(jsonPath("$.data.state").value("DRAFT"))
                .andExpect(jsonPath("$.data.applicants[0].rowNumber").value(6))
                .andExpect(jsonPath("$.data.applicants[0].citizenId").value("012345678901"))
                .andExpect(jsonPath("$.data.applicants[0].reusingAccount").value(true))
                .andExpect(jsonPath("$.data.applicants[0].reuseAccount").doesNotExist())
                .andExpect(jsonPath("$.data.applicants[0].warnings").isEmpty());
    }

    @Test
    void preservesTheBusinessErrorEnvelopeForModuleExceptions() throws Exception {
        when(planning.getPlan(7L)).thenThrow(new AdmissionPlanException(ErrorCode.ADMISSION_STALE_PLAN));

        mvc.perform(get("/api/v1/student-admissions/plans/7"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.ADMISSION_STALE_PLAN.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ADMISSION_STALE_PLAN.getMessage()))
                .andExpect(jsonPath("$.path").value("/api/v1/student-admissions/plans/7"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }
}
