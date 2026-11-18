package com.eduhub.edu_hub.admission.api.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.admission.api.dto.request.CreateAdmissionPlanFromFileRequest;
import com.eduhub.edu_hub.admission.api.dto.request.ImportAdmissionApplicantsRequest;
import com.eduhub.edu_hub.admission.api.dto.request.PrepareAdmissionClassesRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionApplicantResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionCapacityResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanResponse;
import com.eduhub.edu_hub.admission.api.exception.AdmissionExceptionHandler;
import com.eduhub.edu_hub.admission.application.exception.AdmissionImportScopeException;
import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.application.service.AdmissionAllocationService;
import com.eduhub.edu_hub.admission.application.service.AdmissionClassPreparationService;
import com.eduhub.edu_hub.admission.application.service.AdmissionConfirmationService;
import com.eduhub.edu_hub.admission.application.service.AdmissionImportService;
import com.eduhub.edu_hub.admission.application.service.AdmissionNotificationQueryService;
import com.eduhub.edu_hub.admission.application.service.AdmissionPlanningService;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.config.TimeConfig;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
import java.time.Clock;
import java.time.Instant;
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

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-18T17:30:00Z"),
            new TimeConfig().applicationClock().getZone());
    private final ApiResponseFactory apiResponseFactory = new ApiResponseFactory(CLOCK);
    private final AdmissionPlanningService planning = mock(AdmissionPlanningService.class);
    private final AdmissionImportService admissionImportService = mock(AdmissionImportService.class);
    private final AdmissionClassPreparationService classPreparationService =
            mock(AdmissionClassPreparationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        when(planning.getDefaultCapacityConfiguration()).thenReturn(new AdmissionCapacityResponse(40, 50, 15));
        mvc = MockMvcBuilders.standaloneSetup(new StudentAdmissionController(
                        apiResponseFactory,
                        planning,
                        admissionImportService,
                        classPreparationService,
                        mock(AdmissionAllocationService.class),
                        mock(AdmissionConfirmationService.class),
                        mock(AdmissionNotificationQueryService.class)))
                .setControllerAdvice(
                        new AdmissionExceptionHandler(apiResponseFactory),
                        new GlobalExceptionHandler(apiResponseFactory))
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

    @ParameterizedTest
    @ValueSource(ints = {120, -1})
    void noLongerExposesPlanCreationFromAnEstimatedPopulation(int totalStudents) throws Exception {
        mvc.perform(post("/api/v1/student-admissions/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"majorId\":1,\"cohortId\":8,\"totalStudents\":" + totalStudents + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ENDPOINT_NOT_FOUND.getCode()));
        verifyNoInteractions(planning);
    }

    @Test
    void listsClassesAtTheExistingUrlFromTheAdmissionController() throws Exception {
        when(classPreparationService.listAdministrativeClasses(1L, 8L)).thenReturn(List.of());
        mvc.perform(get("/api/v1/administrative-classes").param("majorId", "1").param("cohortId", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        verify(classPreparationService).listAdministrativeClasses(1L, 8L);
    }

    @Test
    void preparesClassesAtTheExistingUrlWithTheSameRequestFields() throws Exception {
        mvc.perform(
                        post("/api/v1/administrative-classes/from-admission-plan")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"planId":1,"expectedRevision":0,"classCount":2,
                                 "acceptReserve":false,"acceptBelowMinimum":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(classPreparationService)
                .prepareClasses(new PrepareAdmissionClassesRequest(1L, 0, 2, null, false, false, null));
    }

    @Test
    void stillRejectsMissingClassScopeAndInvalidClassCount() throws Exception {
        mvc.perform(get("/api/v1/administrative-classes").param("majorId", "1")).andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/administrative-classes/from-admission-plan")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"planId":1,"expectedRevision":0,"classCount":0,
                                 "acceptReserve":false,"acceptBelowMinimum":false}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(classPreparationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]"})
    void preservesTheDifferenceBetweenAutomaticReuseAndCreatingAllClasses(String existingClassIdsJson)
            throws Exception {
        mvc.perform(post("/api/v1/administrative-classes/from-admission-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"planId":1,"expectedRevision":0,"classCount":2,"existingClassIds":%s,
                                 "acceptReserve":false,"acceptBelowMinimum":false}
                                """
                                        .formatted(existingClassIdsJson)))
                .andExpect(status().isOk());
        verify(classPreparationService)
                .prepareClasses(new PrepareAdmissionClassesRequest(
                        1L, 0, 2, existingClassIdsJson.equals("null") ? null : List.of(), false, false, null));
    }

    @Test
    void keepsTheAdmissionErrorHandlerForClassPreparationAfterMergingControllers() throws Exception {
        when(classPreparationService.prepareClasses(any()))
                .thenThrow(new AdmissionPlanException(ErrorCode.ADMISSION_STALE_PLAN));
        mvc.perform(
                        post("/api/v1/administrative-classes/from-admission-plan")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"planId":1,"expectedRevision":0,"classCount":2,
                                 "acceptReserve":false,"acceptBelowMinimum":false}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.ADMISSION_STALE_PLAN.getCode()))
                .andExpect(jsonPath("$.path").value("/api/v1/administrative-classes/from-admission-plan"));
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
