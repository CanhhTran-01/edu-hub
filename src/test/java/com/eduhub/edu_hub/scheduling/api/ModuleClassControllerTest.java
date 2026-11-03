package com.eduhub.edu_hub.scheduling.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.scheduling.api.controller.ModuleClassController;
import com.eduhub.edu_hub.scheduling.api.dto.*;
import com.eduhub.edu_hub.scheduling.api.exception.ModuleClassExceptionHandler;
import com.eduhub.edu_hub.scheduling.application.ModuleClassApplicationService;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassConflictException;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassOpeningException;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.*;
import java.sql.SQLException;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ModuleClassControllerTest {
    private static final String BODY = """
            {"subjects":[{"subjectId":1,"classes":[{}]}]}
            """;

    @Mock
    private ModuleClassApplicationService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ModuleClassController(service))
                .setControllerAdvice(new GlobalExceptionHandler(), new ModuleClassExceptionHandler())
                .build();
    }

    @Test
    void createsBatchWithDefaultCapacityAndReturnsCreatedClasses() throws Exception {
        when(service.createBatch(eq(7L), any()))
                .thenReturn(List.of(ModuleClassResponse.builder()
                        .id(101L)
                        .moduleClassCode("MC_7_CS101_001")
                        .maxCapacity(80)
                        .status(ModuleClassStatus.UNSCHEDULED)
                        .schedules(List.of())
                        .build()));
        mvc.perform(post("/api/v1/semesters/7/module-classes/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].status").value("UNSCHEDULED"))
                .andExpect(jsonPath("$.data[0].schedules").isEmpty());
        var request = ArgumentCaptor.forClass(CreateModuleClassesRequest.class);
        verify(service).createBatch(eq(7L), request.capture());
        assertThat(request.getValue()
                        .getSubjects()
                        .getFirst()
                        .getClasses()
                        .getFirst()
                        .getMaxCapacity())
                .isEqualTo(80);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"subjects\":[]}",
                "{\"subjects\":[null]}",
                "{\"subjects\":[{\"subjectId\":1,\"classes\":[]}]}",
                "{\"subjects\":[{\"subjectId\":1,\"classes\":[null]}]}",
                "{\"subjects\":[{\"subjectId\":1,\"classes\":[{\"maxCapacity\":81}]}]}",
                "{\"subjects\":[{\"subjectId\":1,\"classes\":[{\"maxCapacity\":null}]}]}",
                "{\"subjects\":[{\"subjectId\":1,\"classes\":[{\"maxCapacity\":10.5}]}]}"
            })
    void validatesEveryNestedItemBeforeCallingService(String body) throws Exception {
        mvc.perform(post("/api/v1/semesters/7/module-classes/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(service);
    }

    @Test
    void confirmationConflictKeepsModuleSpecificDetailsWhenGlobalHandlerIsAlsoRegistered() throws Exception {
        when(service.createBatch(eq(7L), any()))
                .thenThrow(new ModuleClassOpeningException(
                        ErrorCode.MODULE_CLASS_CONFIRMATION_REQUIRED,
                        List.of(new FieldErrorDetail("subjects[0].subjectId", "CS101 đã có 2 lớp chưa hủy"))));
        mvc.perform(post("/api/v1/semesters/7/module-classes/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4313))
                .andExpect(jsonPath("$.errors[0].field").value("subjects[0].subjectId"))
                .andExpect(jsonPath("$.path").value("/api/v1/semesters/7/module-classes/batch"));
    }

    @Test
    void codeConstraintConflictDoesNotLeakSql() throws Exception {
        var cause = new ConstraintViolationException("SQL detail", new SQLException(), "uk_module_classes_sequence");
        when(service.createBatch(eq(7L), any())).thenThrow(new DataIntegrityViolationException("SQL detail", cause));
        mvc.perform(post("/api/v1/semesters/7/module-classes/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4318));
    }

    @Test
    void unavailableHistoryReturns503InsteadOfAnEmptyRecommendation() throws Exception {
        when(service.findOpeningSubjects(7L)).thenThrow(new BaseException(ErrorCode.PLANNING_DATA_UNAVAILABLE));
        mvc.perform(get("/api/v1/semesters/7/opening-subjects"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(4317));
    }

    @Test
    void emptyRecommendationsHaveAnExplicitMessage() throws Exception {
        when(service.findOpeningSubjects(7L)).thenReturn(List.of());
        mvc.perform(get("/api/v1/semesters/7/opening-subjects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.message").value("Không có học phần phù hợp để đề xuất mở lớp"));
    }

    @Test
    void patchesCapacityAndReturnsUpdatedClass() throws Exception {
        when(service.update(eq(101L), any()))
                .thenReturn(ModuleClassResponse.builder()
                        .id(101L)
                        .lecturerId(5001L)
                        .maxCapacity(60)
                        .status(ModuleClassStatus.UNSCHEDULED)
                        .build());
        mvc.perform(patch("/api/v1/module-classes/101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxCapacity\":60,\"reason\":\"Điều chỉnh quy mô\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxCapacity").value(60));
        var request = ArgumentCaptor.forClass(UpdateModuleClassRequest.class);
        verify(service).update(eq(101L), request.capture());
        assertThat(request.getValue().getMaxCapacity()).isEqualTo(60);
        assertThat(request.getValue().getReason()).isEqualTo("Điều chỉnh quy mô");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"reason\":\"Không có thay đổi\"}",
                "{\"maxCapacity\":60}",
                "{\"maxCapacity\":60,\"reason\":\"   \"}",
                "{\"maxCapacity\":null,\"reason\":\"Đổi sức chứa\"}",
                "{\"maxCapacity\":10.5,\"reason\":\"Đổi sức chứa\"}",
                "{\"maxCapacity\":81,\"reason\":\"Đổi sức chứa\"}",
                "{\"maxCapacity\":0,\"reason\":\"Đổi sức chứa\"}"
            })
    void patchRequiresValidCapacityAndReason(String body) throws Exception {
        mvc.perform(patch("/api/v1/module-classes/101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(service);
    }

    @Test
    void capacityConflictKeepsCurrentSizeDetailsThroughTheModuleHandler() throws Exception {
        when(service.update(eq(101L), any()))
                .thenThrow(new ModuleClassConflictException(
                        ErrorCode.MODULE_CLASS_CAPACITY_BELOW_SIZE,
                        List.of(new FieldErrorDetail("maxCapacity", "Sĩ số hiện có: 50"))));
        mvc.perform(patch("/api/v1/module-classes/101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxCapacity\":40,\"reason\":\"Đổi sức chứa\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4322))
                .andExpect(jsonPath("$.errors[0].message").value("Sĩ số hiện có: 50"));
    }

    @Test
    void cancellationReturnsTheRetainedCancelledClass() throws Exception {
        when(service.cancel(eq(101L), any()))
                .thenReturn(ModuleClassResponse.builder()
                        .id(101L)
                        .moduleClassCode("MC_9_CS101_001")
                        .status(ModuleClassStatus.CANCELLED)
                        .build());
        mvc.perform(post("/api/v1/module-classes/101/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Không đủ nhu cầu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reason\":null}", "{\"reason\":\"   \"}"})
    void cancellationRequiresAReason(String body) throws Exception {
        mvc.perform(post("/api/v1/module-classes/101/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(service);
    }

    @Test
    void unavailableCancellationHistoryIsReportedInsteadOfAllowingCancellation() throws Exception {
        when(service.cancel(eq(101L), any()))
                .thenThrow(new BaseException(ErrorCode.MODULE_CLASS_ENROLLMENT_DATA_UNAVAILABLE));
        mvc.perform(post("/api/v1/module-classes/101/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Không đủ nhu cầu\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(4325));
    }

    @Test
    void searchValidatesPagination() throws Exception {
        mvc.perform(get("/api/v1/module-classes").param("size", "0")).andExpect(status().isBadRequest());
        verify(service, never()).search(any());
    }
}
