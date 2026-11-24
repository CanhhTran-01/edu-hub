package com.eduhub.edu_hub.people.api.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.people.application.service.FacultyQueryService;
import com.eduhub.edu_hub.people.domain.model.Faculty;
import com.eduhub.edu_hub.people.domain.repository.FacultyRepository;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.config.TimeConfig;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FacultyControllerTest {
    private final FacultyRepository repository = mock(FacultyRepository.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new FacultyController(
                            new ApiResponseFactory(
                                    Clock.fixed(Instant.parse("2026-11-24T00:00:00Z"), TimeConfig.APPLICATION_ZONE)),
                            new FacultyQueryService(repository)))
            .build();

    @Test
    void listsInactiveAndUnconfiguredFacultiesWithoutExposingEntities() throws Exception {
        var faculty = new Faculty();
        faculty.setId(1L);
        faculty.setFacultyName("Công nghệ thông tin");
        faculty.setActive(false);
        when(repository.findAllByDeletedFalseOrderByFacultyNameAscIdAsc()).thenReturn(List.of(faculty));
        mvc.perform(get("/api/v1/faculties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].facultyName").value("Công nghệ thông tin"))
                .andExpect(jsonPath("$.data[0].active").value(false))
                .andExpect(jsonPath("$.data[0].deleted").doesNotExist())
                .andExpect(jsonPath("$.data[0].createdAt").doesNotExist());
        verify(repository).findAllByDeletedFalseOrderByFacultyNameAscIdAsc();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void returnsAnEmptyCatalog() throws Exception {
        when(repository.findAllByDeletedFalseOrderByFacultyNameAscIdAsc()).thenReturn(List.of());
        mvc.perform(get("/api/v1/faculties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
