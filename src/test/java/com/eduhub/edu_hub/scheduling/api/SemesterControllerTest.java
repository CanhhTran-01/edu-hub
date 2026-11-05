package com.eduhub.edu_hub.scheduling.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduhub.edu_hub.scheduling.api.controller.SemesterController;
import com.eduhub.edu_hub.scheduling.api.dto.request.CreateSemesterRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.SemesterSearchRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.UpdateSemesterRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import com.eduhub.edu_hub.scheduling.api.exception.SemesterExceptionHandler;
import com.eduhub.edu_hub.scheduling.application.exception.SemesterConflictException;
import com.eduhub.edu_hub.scheduling.application.SemesterApplicationService;
import com.eduhub.edu_hub.scheduling.domain.enums.SemesterStatus;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
import java.sql.SQLException;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SemesterControllerTest {
    private static final String VALID_BODY =
            """
            {
              "semesterName": "Học kì 1 năm học 2026-2027",
              "startDate": "2026-09-01",
              "endDate": "2026-12-31",
              "registrationStart": "2026-08-10",
              "registrationEnd": "2026-08-20"
            }
            """;

    @Mock
    private SemesterApplicationService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SemesterController(service))
                .setControllerAdvice(new GlobalExceptionHandler(), new SemesterExceptionHandler())
                .build();
    }

    @Test
    void createsSemesterAndReturns201WithApiEnvelope() throws Exception {
        var semester = SemesterResponse.builder()
                .id(1L)
                .semesterCode("HK_1_2026_2027")
                .status(SemesterStatus.UPCOMING)
                .build();
        when(service.createSemester(any(CreateSemesterRequest.class))).thenReturn(semester);

        mvc.perform(post("/api/v1/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.semesterCode").value("HK_1_2026_2027"))
                .andExpect(jsonPath("$.data.status").value("UPCOMING"));
    }

    @Test
    void missingDateReturnsFieldValidationErrorBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"2026-08-20\"", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.errors[0].field").value("registrationEnd"));

        verifyNoInteractions(service);
    }

    @Test
    void malformedDateReturns400InsteadOf500() throws Exception {
        mvc.perform(post("/api/v1/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("2026-08-20", "not-a-date")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verifyNoInteractions(service);
    }

    @Test
    void searchReturnsEmptyPaginatedResult() throws Exception {
        when(service.searchSemesters(any(SemesterSearchRequest.class)))
                .thenReturn(PageResponse.<SemesterResponse>builder()
                        .content(List.of())
                        .pageNumber(0)
                        .pageSize(20)
                        .totalElements(0)
                        .totalPages(0)
                        .isLast(true)
                        .build());

        mvc.perform(get("/api/v1/semesters").param("status", "UPCOMING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Không tìm thấy kết quả"))
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void invalidPaginationAndStatusReturn400() throws Exception {
        mvc.perform(get("/api/v1/semesters").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
        mvc.perform(get("/api/v1/semesters").param("status", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verifyNoInteractions(service);
    }

    @Test
    void invalidIdReturns400() throws Exception {
        mvc.perform(get("/api/v1/semesters/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"));

        verifyNoInteractions(service);
    }

    @Test
    void missingSemesterReturns404() throws Exception {
        when(service.getSemesterById(99L)).thenThrow(new BaseException(ErrorCode.SEMESTER_NOT_FOUND));

        mvc.perform(get("/api/v1/semesters/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.SEMESTER_NOT_FOUND.getCode()));
    }

    @Test
    void updatesSemesterWithFullRequestBody() throws Exception {
        when(service.updateSemester(eq(1L), any(UpdateSemesterRequest.class)))
                .thenReturn(SemesterResponse.builder()
                        .id(1L)
                        .semesterCode("HK_1_2026_2027")
                        .build());

        mvc.perform(put("/api/v1/semesters/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật kỳ học thành công"))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void doesNotExposeDeleteEndpoint() throws Exception {
        mvc.perform(delete("/api/v1/semesters/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.METHOD_NOT_ALLOWED.getCode()));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"SEMESTER_DATE_OVERLAP,studyPeriod", "SEMESTER_REGISTRATION_OVERLAP,registrationPeriod"})
    void conflictResponseIncludesTheConflictingSemesterAndPeriod(ErrorCode error, String field) throws Exception {
        when(service.createSemester(any(CreateSemesterRequest.class)))
                .thenThrow(new SemesterConflictException(
                        error,
                        List.of(new FieldErrorDetail(field, "Học kì 2 năm học 2026-2027 (2026-08-10 - 2026-08-20)"))));

        mvc.perform(post("/api/v1/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(error.getCode()))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("Học kì 2 năm học 2026-2027")))
                .andExpect(jsonPath("$.path").value("/api/v1/semesters"));
    }

    @ParameterizedTest
    @CsvSource({
        "uk_semesters_name,DUPLICATE_SEMESTER_NAME",
        "uk_semesters_code,DUPLICATE_SEMESTER_NAME",
        "ex_semesters_study_period,SEMESTER_DATE_OVERLAP",
        "ex_semesters_registration_period,SEMESTER_REGISTRATION_OVERLAP"
    })
    void databaseConstraintsFromConcurrentWritesReturn409(String constraint, ErrorCode error) throws Exception {
        var databaseError =
                new ConstraintViolationException("Constraint rejected", new SQLException("rejected"), constraint);
        when(service.createSemester(any(CreateSemesterRequest.class)))
                .thenThrow(new DataIntegrityViolationException("Could not save", databaseError));

        mvc.perform(post("/api/v1/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(error.getCode()));
    }
}
