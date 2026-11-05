package com.eduhub.edu_hub.people.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduhub.edu_hub.people.api.dto.StudentResponse;
import com.eduhub.edu_hub.people.application.StudentAdmissionService;
import com.eduhub.edu_hub.people.application.StudentApplicationService;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StudentStatusControllerTest {
    private StudentApplicationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(StudentApplicationService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new StudentController(service, mock(StudentAdmissionService.class)))
                .build();
    }

    @Test
    void deactivatesStudentWithReasonAndBearerToken() throws Exception {
        StudentResponse response = StudentResponse.builder()
                .id(5L)
                .activityStatus(ActivityStatus.DEACTIVATED)
                .build();
        when(service.deactivate(5L, "Bảo lưu", "access-token")).thenReturn(response);

        mvc.perform(post("/api/v1/students/5/deactivate")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Bảo lưu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityStatus").value("DEACTIVATED"));

        verify(service).deactivate(5L, "Bảo lưu", "access-token");
    }

    @Test
    void activatesStudentWithReasonAndBearerToken() throws Exception {
        StudentResponse response = StudentResponse.builder()
                .id(5L)
                .activityStatus(ActivityStatus.STUDYING)
                .build();
        when(service.activate(5L, "Quay lại học", "access-token")).thenReturn(response);

        mvc.perform(post("/api/v1/students/5/activate")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Quay lại học\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityStatus").value("STUDYING"));

        verify(service).activate(5L, "Quay lại học", "access-token");
    }

    @Test
    void rejectsBlankReason() throws Exception {
        mvc.perform(post("/api/v1/students/5/deactivate")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
    }
}
