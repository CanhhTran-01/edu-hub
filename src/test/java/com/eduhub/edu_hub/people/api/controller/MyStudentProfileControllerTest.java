package com.eduhub.edu_hub.people.api.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.eduhub.edu_hub.people.api.exception.MyStudentProfileExceptionHandler;
import com.eduhub.edu_hub.people.application.MyStudentProfileService;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MyStudentProfileControllerTest {
    MyStudentProfileService service = mock(MyStudentProfileService.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new MyStudentProfileController(service))
                .setControllerAdvice(new MyStudentProfileExceptionHandler(), new GlobalExceptionHandler()).build();
    }

    @Test void getAndPatchResolveLiteralMeRoute() throws Exception {
        mvc.perform(get("/api/v1/students/me").header("Authorization", "Bearer token")).andExpect(status().isOk());
        verify(service).get("token");
        mvc.perform(patch("/api/v1/students/me").header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"0900000000\"}"))
                .andExpect(status().isOk());
        verify(service).update(eq("token"), any());
    }

    @ParameterizedTest @ValueSource(strings = {"-0.01", "4.01"})
    void rejectsGpaOutOfRange(String gpa) throws Exception {
        mvc.perform(patch("/api/v1/students/me").header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetGpa\":" + gpa + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("targetGpa"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "4", "null"})
    void acceptsGpaBoundaries(String gpa) throws Exception {
        mvc.perform(patch("/api/v1/students/me").header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetGpa\":" + gpa + "}"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest @ValueSource(strings = {"studentCode", "fullName", "userId", "studentId", "administrativeClassId",
            "majorId", "cohortId", "trainingType", "activityStatus", "email", "address", "createdBy", "actualGpa"})
    void rejectsLockedAndUnsupportedFields(String field) throws Exception {
        mvc.perform(patch("/api/v1/students/me").header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"" + field + "\":null}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void rejectsNonBearerHeader() throws Exception {
        mvc.perform(get("/api/v1/students/me").header("Authorization", "Basic token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
