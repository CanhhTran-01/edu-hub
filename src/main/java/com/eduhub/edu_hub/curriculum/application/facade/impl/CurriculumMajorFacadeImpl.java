package com.eduhub.edu_hub.curriculum.application.facade.impl;

import com.eduhub.edu_hub.curriculum.api.dto.response.MajorReferenceResponse;
import com.eduhub.edu_hub.curriculum.application.exception.MajorReferenceException;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.repository.MajorRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurriculumMajorFacadeImpl implements CurriculumFacade.Majors {
    private final MajorRepository majorRepository;

    @Override
    public List<MajorReferenceResponse> findMajorsForClassNaming() {
        return majorRepository.findByDeletedFalseOrderById().stream()
                .map(major -> new MajorReferenceResponse(major.getId(), major.getMajorCode(), major.getMajorName()))
                .toList();
    }

    @Override
    public MajorReferenceResponse requireMajor(Long majorId) {
        return majorRepository
                .findByIdAndDeletedFalse(majorId)
                .map(major -> new MajorReferenceResponse(major.getId(), major.getMajorCode(), major.getMajorName()))
                .orElseThrow(MajorReferenceException::new);
    }
}
