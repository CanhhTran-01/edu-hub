package com.eduhub.edu_hub.scheduling.api.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.scheduling.api.dto.request.ExportModuleClassStudentsRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.ModuleClassRosterSearchRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassRosterResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassSimpleResponse;
import com.eduhub.edu_hub.scheduling.api.exception.ModuleClassRosterExceptionHandler;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassRosterException;
import com.eduhub.edu_hub.scheduling.application.service.ModuleClassExportService;
import com.eduhub.edu_hub.scheduling.application.service.ModuleClassRosterService;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassExportColumn;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
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

class ModuleClassRosterControllerTest {
    private final ModuleClassRosterService rosterService = mock(ModuleClassRosterService.class);
    private final ModuleClassExportService exportService = mock(ModuleClassExportService.class);
    private final Jwt principal =
            Jwt.withTokenValue("token").header("alg", "HS256").claim("uid", 9L).build();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ModuleClassRosterController(rosterService, exportService))
                .setControllerAdvice(new GlobalExceptionHandler(), new ModuleClassRosterExceptionHandler())
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
    void passesAuthenticatedPrincipalSemesterAndKeywordWithoutAcceptingClientScope() throws Exception {
        when(rosterService.searchClasses(eq(principal), eq(7L), any()))
                .thenReturn(PageResponse.<ModuleClassSimpleResponse>builder()
                        .content(List.of())
                        .pageNumber(0)
                        .pageSize(20)
                        .totalElements(0)
                        .totalPages(0)
                        .isLast(true)
                        .build());
        mvc.perform(get("/api/v1/semesters/7/module-classes/export-options")
                        .param("keyword", "CS101")
                        .param("lecturerId", "999")
                        .param("role", "ACADEMIC_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.last").value(true));
        var request = ArgumentCaptor.forClass(ModuleClassRosterSearchRequest.class);
        verify(rosterService).searchClasses(eq(principal), eq(7L), request.capture());
        assertThat(request.getValue().getKeyword()).isEqualTo("CS101");
    }

    @Test
    void returnsClassDetailsAndEmptyRosterInJson() throws Exception {
        when(rosterService.findRoster(principal, 100L))
                .thenReturn(new ModuleClassRosterResponse(
                        ModuleClassResponse.builder().id(100L).build(), List.of()));
        mvc.perform(get("/api/v1/module-classes/100/roster"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.moduleClass.id").value(100))
                .andExpect(jsonPath("$.data.students").isEmpty());
    }

    @Test
    void returnsDownloadBytesWithDispositionAndNoStoreInsteadOfJsonEnvelope() throws Exception {
        when(exportService.exportStudents(eq(principal), eq(100L), any())).thenReturn(new byte[] {1, 2, 3});
        mvc.perform(post("/api/v1/module-classes/100/students/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(
                        header().string("Content-Disposition", "attachment; filename=module-class-100-students.xlsx"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(new byte[] {1, 2, 3}));
        var request = ArgumentCaptor.forClass(ExportModuleClassStudentsRequest.class);
        verify(exportService).exportStudents(eq(principal), eq(100L), request.capture());
        assertThat(request.getValue().getColumns()).containsExactly(ModuleClassExportColumn.values());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"columns\":null}",
                "{\"columns\":[]}",
                "{\"columns\":[null]}",
                "{\"columns\":[\"CITIZEN_ID\"]}",
                "{\"columns\":[0]}",
                "{\"columns\":[true]}"
            })
    void rejectsInvalidColumnJsonBeforeExport(String json) throws Exception {
        mvc.perform(post("/api/v1/module-classes/100/students/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(exportService);
    }

    @Test
    void forbiddenAndFileCreationFailuresRemainJson() throws Exception {
        when(exportService.exportStudents(eq(principal), eq(100L), any()))
                .thenThrow(new ModuleClassRosterException(ErrorCode.UNAUTHORIZED))
                .thenThrow(new ModuleClassRosterException(ErrorCode.MODULE_CLASS_EXPORT_FAILED));
        mvc.perform(post("/api/v1/module-classes/100/students/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001))
                .andExpect(header().doesNotExist("Content-Disposition"));
        mvc.perform(post("/api/v1/module-classes/100/students/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(4342))
                .andExpect(jsonPath("$.message").value("Không thể tạo tệp danh sách sinh viên, vui lòng thử lại"));
    }

    @Test
    void keepsColumnValidationFieldErrors() throws Exception {
        when(exportService.exportStudents(eq(principal), eq(100L), any()))
                .thenThrow(new ModuleClassRosterException(
                        ErrorCode.VALIDATION_ERROR, List.of(new FieldErrorDetail("columns", "Thiếu cột MSSV"))));
        mvc.perform(post("/api/v1/module-classes/100/students/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("columns"));
    }

    @Test
    void rejectsInvalidPagination() throws Exception {
        mvc.perform(get("/api/v1/semesters/7/module-classes/export-options").param("page", "-1"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(rosterService);
    }
}
