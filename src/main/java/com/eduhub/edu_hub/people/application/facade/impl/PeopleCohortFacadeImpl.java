package com.eduhub.edu_hub.people.application.facade.impl;

import com.eduhub.edu_hub.people.api.dto.response.CohortResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.repository.CohortRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cung cấp dữ liệu khóa cho module khác, giữ entity và repository bên trong People. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeopleCohortFacadeImpl implements PeopleFacade.Cohorts {
    private static final int COHORT_BATCH_SIZE = 500;
    private final CohortRepository cohortRepository;

    @Override
    public List<CohortResponse> findCohortsByIds(Set<Long> cohortIds) {
        var orderedCohortIds = cohortIds.stream().sorted().toList();
        var cohortResponses = new ArrayList<CohortResponse>();
        for (int startIndex = 0; startIndex < orderedCohortIds.size(); startIndex += COHORT_BATCH_SIZE) {
            var cohortBatchIds = Set.copyOf(orderedCohortIds.subList(
                    startIndex, Math.min(startIndex + COHORT_BATCH_SIZE, orderedCohortIds.size())));
            cohortRepository.findAllByIdInAndDeletedFalseOrderByIdAsc(cohortBatchIds).stream()
                    .map(cohort -> new CohortResponse(
                            cohort.getId(), cohort.getCohortCode(), cohort.getStartYear(), cohort.getEndYear()))
                    .forEach(cohortResponses::add);
        }
        return cohortResponses;
    }
}
