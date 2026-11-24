package com.eduhub.edu_hub.people.application.service;

import com.eduhub.edu_hub.people.api.dto.response.CohortResponse;
import com.eduhub.edu_hub.people.domain.repository.CohortRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CohortQueryService {
    private final CohortRepository cohortRepository;

    public List<CohortResponse> findCohorts() {
        // Giữ khóa cũ và năm chưa khai báo để mở lại phương án; điều kiện nhập học được kiểm tra khi thao tác.
        return cohortRepository.findAllByDeletedFalseOrderByIdDesc().stream()
                .map(cohort -> new CohortResponse(
                        cohort.getId(), cohort.getCohortCode(), cohort.getStartYear(), cohort.getEndYear()))
                .toList();
    }
}
