package com.eduhub.edu_hub.people.application.facade.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.people.api.dto.response.CohortResponse;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.repository.CohortRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class PeopleCohortFacadeImplTest {
    private final CohortRepository cohortRepository = mock(CohortRepository.class);
    private final PeopleCohortFacadeImpl facade = new PeopleCohortFacadeImpl(cohortRepository);

    @Test
    void emptyIdsDoNotQueryPeople() {
        assertThat(facade.findCohortsByIds(Set.of())).isEmpty();
        verifyNoInteractions(cohortRepository);
    }

    @Test
    void readsUndeletedCohortsInBatchesWithoutRequiringAdmissionCodeOrYears() {
        var cohortIds = LongStream.rangeClosed(1, 501).boxed().collect(Collectors.toSet());
        var firstBatch = LongStream.rangeClosed(1, 500).boxed().collect(Collectors.toSet());
        var legacyCohort = new Cohort();
        legacyCohort.setId(1L);
        legacyCohort.setCohortCode("K1");
        var currentCohort = new Cohort();
        currentCohort.setId(501L);
        currentCohort.setCohortCode("K501");
        currentCohort.setStartYear(2026);
        currentCohort.setEndYear(2030);
        when(cohortRepository.findAllByIdInAndDeletedFalseOrderByIdAsc(firstBatch))
                .thenReturn(List.of(legacyCohort));
        when(cohortRepository.findAllByIdInAndDeletedFalseOrderByIdAsc(Set.of(501L)))
                .thenReturn(List.of(currentCohort));

        assertThat(facade.findCohortsByIds(cohortIds))
                .containsExactly(
                        new CohortResponse(1L, "K1", null, null), new CohortResponse(501L, "K501", 2026, 2030));
        verify(cohortRepository).findAllByIdInAndDeletedFalseOrderByIdAsc(firstBatch);
        verify(cohortRepository).findAllByIdInAndDeletedFalseOrderByIdAsc(Set.of(501L));
        verifyNoMoreInteractions(cohortRepository);
    }
}
