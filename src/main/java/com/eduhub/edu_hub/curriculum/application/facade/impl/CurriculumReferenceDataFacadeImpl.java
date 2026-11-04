package com.eduhub.edu_hub.curriculum.application.facade.impl;

import com.eduhub.edu_hub.curriculum.api.dto.CurriculumAssignmentResponse;
import com.eduhub.edu_hub.curriculum.api.dto.MajorSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.enums.CatalogStatus;
import com.eduhub.edu_hub.curriculum.domain.repository.CurriculumRepository;
import com.eduhub.edu_hub.curriculum.domain.repository.MajorRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurriculumReferenceDataFacadeImpl implements CurriculumFacade.ReferenceData {
    private final MajorRepository majors;
    private final CurriculumRepository curriculums;

    @Override
    public List<MajorSimpleResponse> findMajorsByIds(Set<Long> ids) {
        if (ids.isEmpty()) return List.of();
        // Giữ tên ngành lịch sử khi xem hồ sơ cũ, kể cả ngành đã ngừng sử dụng/ẩn.
        return majors.findAllById(ids).stream()
                .map(major -> new MajorSimpleResponse(
                        major.getId(),
                        major.getMajorCode(),
                        major.getMajorName(),
                        major.getFaculty() == null ? null : major.getFaculty().getId()))
                .toList();
    }

    @Override
    public List<CurriculumAssignmentResponse> findCurriculumAssignments(Set<Long> majorIds, Set<Long> cohortIds) {
        if (majorIds.isEmpty() || cohortIds.isEmpty()) return List.of();
        return curriculums.findAllByMajor_IdInAndCohort_IdInAndDeletedFalse(majorIds, cohortIds).stream()
                .filter(curriculum -> curriculum.getStatus() == CatalogStatus.ACTIVE
                        && !curriculum.getMajor().isDeleted()
                        && curriculum.getMajor().getStatus() == CatalogStatus.ACTIVE)
                .map(curriculum -> new CurriculumAssignmentResponse(
                        curriculum.getId(),
                        curriculum.getCurriculumCode(),
                        curriculum.getMajor().getId(),
                        curriculum.getCohort().getId(),
                        curriculum.getTrainingType()))
                .toList();
    }
}
