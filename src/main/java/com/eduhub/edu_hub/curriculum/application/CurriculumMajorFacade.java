package com.eduhub.edu_hub.curriculum.application;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.MajorResponse;
import com.eduhub.edu_hub.curriculum.domain.repository.MajorRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurriculumMajorFacade implements CurriculumFacade.Majors {
    private final MajorRepository majors;

    public java.util.List<MajorResponse> findMajorsForClassNaming() {
        return majors.findByDeletedFalseOrderById().stream()
                .map(major -> new MajorResponse(major.getId(), major.getMajorCode(), major.getMajorName()))
                .toList();
    }

    public MajorResponse requireMajor(Long id) {
        return majors.findByIdAndDeletedFalse(id)
                .map(m -> new MajorResponse(m.getId(), m.getMajorCode(), m.getMajorName()))
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_STUDENT_CODE_MAJOR));
    }
}
