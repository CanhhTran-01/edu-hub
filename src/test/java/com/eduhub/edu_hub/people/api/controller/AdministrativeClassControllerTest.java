package com.eduhub.edu_hub.people.api.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.people.api.dto.request.AdmissionRequests.CreateClassesRequest;
import com.eduhub.edu_hub.people.application.admission.AdministrativeClassService;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdministrativeClassControllerTest {
    private final AdministrativeClassService classes = mock(AdministrativeClassService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AdministrativeClassController(classes))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listsClassesWithoutControllerAuthorizationChecks() throws Exception {
        when(classes.list(1L, 8L)).thenReturn(List.of());
        mvc.perform(get("/api/v1/administrative-classes").param("majorId", "1").param("cohortId", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        verify(classes).list(1L, 8L);
    }

    @Test
    void createsClassesWithoutControllerAuthorizationChecks() throws Exception {
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
        verify(classes).createFromPlan(new CreateClassesRequest(1L, 0, 2, null, false, false, null));
    }

    @Test
    void stillRejectsMissingSelectionAndInvalidClassCount() throws Exception {
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
        verifyNoInteractions(classes);
    }
}
