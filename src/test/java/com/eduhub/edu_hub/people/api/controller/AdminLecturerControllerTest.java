package com.eduhub.edu_hub.people.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduhub.edu_hub.people.api.dto.request.LecturerSearchRequest;
import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.api.exception.LecturerCreationExceptionHandler;
import com.eduhub.edu_hub.people.application.exception.ProfileSearchException;
import com.eduhub.edu_hub.people.application.service.LecturerApplicationService;
import com.eduhub.edu_hub.people.application.service.LecturerCreationService;
import com.eduhub.edu_hub.people.domain.enums.LecturerStatus;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminLecturerControllerTest {
    private final LecturerApplicationService lecturerApplicationService = mock(LecturerApplicationService.class);
    private final LecturerCreationService lecturerCreationService = mock(LecturerCreationService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                    new AdminLecturerController(lecturerApplicationService, lecturerCreationService))
            .setControllerAdvice(new LecturerCreationExceptionHandler(), new GlobalExceptionHandler())
            .build();

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
    void searchUsesDefaultPaginationAndReturnsMatchingLecturers() throws Exception {
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
                .andExpect(jsonPath("$.message").value("Success"))
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
