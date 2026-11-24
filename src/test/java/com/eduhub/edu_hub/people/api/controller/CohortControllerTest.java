package com.eduhub.edu_hub.people.api.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.people.application.service.CohortQueryService;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.repository.CohortRepository;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.config.TimeConfig;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CohortControllerTest {
    private final CohortRepository cohortRepository = mock(CohortRepository.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CohortController(
                    new ApiResponseFactory(
                            Clock.fixed(Instant.parse("2026-11-24T00:00:00Z"), TimeConfig.APPLICATION_ZONE)),
                    new CohortQueryService(cohortRepository)))
            .build();

    @Test
    void listsHistoricalCohortsAndNullableYearsWithoutExposingEntities() throws Exception {
        var currentCohort = new Cohort();
        currentCohort.setId(9L);
        currentCohort.setCohortCode("K09");
        currentCohort.setStartYear(2026);
        currentCohort.setEndYear(2030);
        var historicalCohort = new Cohort();
        historicalCohort.setId(1L);
        historicalCohort.setCohortCode("K01");
        when(cohortRepository.findAllByDeletedFalseOrderByIdDesc())
                .thenReturn(List.of(currentCohort, historicalCohort));

        mvc.perform(get("/api/v1/cohorts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(9))
                .andExpect(jsonPath("$.data[0].cohortCode").value("K09"))
                .andExpect(jsonPath("$.data[0].startYear").value(2026))
                .andExpect(jsonPath("$.data[0].endYear").value(2030))
                .andExpect(jsonPath("$.data[1].cohortCode").value("K01"))
                .andExpect(jsonPath("$.data[1].startYear").value(nullValue()))
                .andExpect(jsonPath("$.data[1].endYear").value(nullValue()))
                .andExpect(jsonPath("$.data[0].codeSequence").doesNotExist())
                .andExpect(jsonPath("$.data[0].deleted").doesNotExist());
        verify(cohortRepository).findAllByDeletedFalseOrderByIdDesc();
        verifyNoMoreInteractions(cohortRepository);
    }

    @Test
    void returnsAnEmptyListWhenNoUndeletedCohortsExist() throws Exception {
        when(cohortRepository.findAllByDeletedFalseOrderByIdDesc()).thenReturn(List.of());

        mvc.perform(get("/api/v1/cohorts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
