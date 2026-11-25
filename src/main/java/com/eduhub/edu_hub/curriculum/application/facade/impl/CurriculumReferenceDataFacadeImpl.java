package com.eduhub.edu_hub.curriculum.application.facade.impl;

import com.eduhub.edu_hub.curriculum.api.dto.response.CurriculumAssignmentResponse;
import com.eduhub.edu_hub.curriculum.api.dto.response.MajorSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.enums.CatalogStatus;
import com.eduhub.edu_hub.curriculum.domain.repository.CurriculumRepository;
import com.eduhub.edu_hub.curriculum.domain.repository.MajorRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurriculumReferenceDataFacadeImpl implements CurriculumFacade.ReferenceData {
    private static final int MAJOR_BATCH_SIZE = 500;
    private final MajorRepository majors;
    private final CurriculumRepository curriculums;

    @Override
    public List<MajorSimpleResponse> findMajorsByIds(Set<Long> ids) {
        if (ids.isEmpty()) return List.of();
        // Giữ tên ngành lịch sử khi xem hồ sơ cũ, kể cả ngành đã ngừng sử dụng/ẩn.
        var orderedMajorIds = ids.stream().sorted().toList();
        var majorResponses = new ArrayList<MajorSimpleResponse>();
        for (int startIndex = 0; startIndex < orderedMajorIds.size(); startIndex += MAJOR_BATCH_SIZE) {
            majors
                    .findAllById(orderedMajorIds.subList(
                            startIndex, Math.min(startIndex + MAJOR_BATCH_SIZE, orderedMajorIds.size())))
                    .stream()
                    .map(major -> new MajorSimpleResponse(
                            major.getId(),
                            major.getMajorCode(),
                            major.getMajorName(),
                            major.getFacultyId(),
                            major.isDeleted()))
                    .forEach(majorResponses::add);
        }
        return majorResponses;
    }

    @Override
    public List<CurriculumAssignmentResponse> findCurriculumAssignments(Set<Long> majorIds, Set<Long> cohortIds) {
        if (majorIds.isEmpty() || cohortIds.isEmpty()) return List.of();
        return curriculums.findAllByMajor_IdInAndCohortIdInAndDeletedFalse(majorIds, cohortIds).stream()
                .filter(curriculum -> curriculum.getStatus() == CatalogStatus.ACTIVE
                        && !curriculum.getMajor().isDeleted()
                        && curriculum.getMajor().getStatus() == CatalogStatus.ACTIVE)
                .map(curriculum -> new CurriculumAssignmentResponse(
                        curriculum.getId(),
                        curriculum.getCurriculumCode(),
                        curriculum.getMajor().getId(),
                        curriculum.getCohortId(),
                        curriculum.getTrainingType()))
                .toList();
    }
}
