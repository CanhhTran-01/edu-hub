package com.eduhub.edu_hub.curriculum.application;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.MajorResponse;
import com.eduhub.edu_hub.curriculum.domain.repository.MajorRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurriculumMajorFacade implements CurriculumFacade.Majors {
    private final MajorRepository majors;

    public MajorResponse requireMajor(Long id) {
        return majors.findByIdAndDeletedFalse(id)
                .map(m -> new MajorResponse(m.getId(), m.getMajorCode(), m.getMajorName()))
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_STUDENT_CODE_MAJOR));
    }

    public Optional<MajorResponse> findMajorByCode(String code) {
        return majors.findByMajorCodeIgnoreCaseAndDeletedFalse(code)
                .map(m -> new MajorResponse(m.getId(), m.getMajorCode(), m.getMajorName()));
    }
}
