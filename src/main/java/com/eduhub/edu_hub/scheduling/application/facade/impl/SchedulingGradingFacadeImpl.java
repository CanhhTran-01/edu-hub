package com.eduhub.edu_hub.scheduling.application.facade.impl;

import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassGradingResponse;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassException;
import com.eduhub.edu_hub.scheduling.application.exception.SemesterException;
import com.eduhub.edu_hub.scheduling.application.facade.SchedulingFacade;
import com.eduhub.edu_hub.scheduling.domain.model.ModuleClass;
import com.eduhub.edu_hub.scheduling.domain.repository.ModuleClassRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.SemesterRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchedulingGradingFacadeImpl implements SchedulingFacade.Grading {
    private final SemesterRepository semesterRepository;
    private final ModuleClassRepository moduleClassRepository;

    @Override
    public ModuleClassGradingResponse findModuleClassForGrading(Long moduleClassId) {
        return toResponse(moduleClassRepository
                .findByIdAndDeletedFalse(moduleClassId)
                .orElseThrow(() -> new ModuleClassException(ErrorCode.MODULE_CLASS_NOT_FOUND)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ModuleClassGradingResponse lockModuleClassForGrading(Long moduleClassId) {
        // Chỉ lấy scalar trước khi chờ khóa để không giữ entity/phân công cũ trong persistence context.
        Long semesterId = moduleClassRepository
                .findSemesterIdByIdAndDeletedFalse(moduleClassId)
                .orElseThrow(() -> new ModuleClassException(ErrorCode.MODULE_CLASS_NOT_FOUND));
        semesterRepository
                .findForRegistrationByIdAndDeletedFalse(semesterId)
                .orElseThrow(() -> new SemesterException(ErrorCode.SEMESTER_NOT_FOUND));
        return toResponse(moduleClassRepository
                .findForUpdateByIdAndDeletedFalse(moduleClassId)
                .orElseThrow(() -> new ModuleClassException(ErrorCode.MODULE_CLASS_NOT_FOUND)));
    }

    private ModuleClassGradingResponse toResponse(ModuleClass moduleClass) {
        var semester = moduleClass.getSemester();
        if (semester.isDeleted()) throw new SemesterException(ErrorCode.SEMESTER_NOT_FOUND);
        return new ModuleClassGradingResponse(
                moduleClass.getId(),
                moduleClass.getModuleClassCode(),
                semester.getId(),
                semester.getSemesterCode(),
                moduleClass.getLecturer() == null
                        ? null
                        : moduleClass.getLecturer().getId(),
                moduleClass.getStatus());
    }
}
