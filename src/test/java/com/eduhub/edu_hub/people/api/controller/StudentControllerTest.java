package com.eduhub.edu_hub.people.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduhub.edu_hub.people.api.dto.request.CreateStudentRequest;
import com.eduhub.edu_hub.people.api.dto.response.CreateStudentResponse;
import com.eduhub.edu_hub.people.api.dto.response.StudentResponse;
import com.eduhub.edu_hub.people.application.StudentApplicationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StudentControllerTest {
    private final StudentApplicationService service = mock(StudentApplicationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new StudentController(service)).build();
    }

    @ParameterizedTest
    @CsvSource({
        "POST,/admission/preview",
        "PUT,/admission/11111111-1111-1111-1111-111111111111/assignment",
        "POST,/admission/11111111-1111-1111-1111-111111111111/confirm"
    })
    void oldAdmissionEndpointsAreNoLongerAvailable(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), "/api/v1/students" + path))
                .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    @Test
    void stillCreatesSingleStudent() throws Exception {
        StudentResponse student = StudentResponse.builder()
                .id(1L)
                .studentCode("CT080101")
                .fullName("Nguyen Van An")
                .build();
        when(service.create(any(CreateStudentRequest.class)))
                .thenReturn(new CreateStudentResponse(student, List.of(), "SENT", null));

        mvc.perform(
                        post("/api/v1/students")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                    "fullName": "Nguyen Van An",
                                    "citizenId": "012345678901",
                                    "email": "an@example.com",
                                    "majorId": 1,
                                    "cohortId": 2,
                                    "administrativeClassId": 3,
                                    "trainingType": "CHINH_QUY",
                                    "reason": "Bo sung sinh vien"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.student.studentCode").value("CT080101"));
        verify(service).create(argThat(value -> value.getAdministrativeClassId().equals(3L)));
    }

    @Test
    void noLongerAllowsManualAccountNotificationResend() throws Exception {
        mvc.perform(post("/api/v1/students/1/account-notification/resend")).andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }
}
