package com.eduhub.edu_hub.scheduling.application.service;

import com.eduhub.edu_hub.enrollment.api.dto.response.ModuleClassUsageResponse;
import com.eduhub.edu_hub.enrollment.application.facade.EnrollmentFacade;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Đọc lịch sử đăng ký qua EnrollmentFacade để kiểm tra hủy lớp, không đọc repository liên module. */
@Service
@RequiredArgsConstructor
public class ModuleClassEnrollmentQueryService {
    private final ObjectProvider<EnrollmentFacade.Planning> enrollmentFacadeProvider;

    /** Đếm mọi bản ghi, kể cả DROPPED và soft delete; không suy ra từ currentSize. */
    public ModuleClassUsageResponse summarizeModuleClassEnrollments(Long moduleClassId) {
        var enrollmentFacade = enrollmentFacadeProvider.getIfAvailable();
        if (enrollmentFacade == null)
            throw new ModuleClassException(ErrorCode.MODULE_CLASS_ENROLLMENT_DATA_UNAVAILABLE);
        var enrollmentUsage = enrollmentFacade.summarizeModuleClass(moduleClassId);
        if (enrollmentUsage == null) throw new ModuleClassException(ErrorCode.MODULE_CLASS_ENROLLMENT_DATA_UNAVAILABLE);
        return enrollmentUsage;
    }
}
