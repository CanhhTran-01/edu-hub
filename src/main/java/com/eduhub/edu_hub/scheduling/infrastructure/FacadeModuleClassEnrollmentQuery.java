package com.eduhub.edu_hub.scheduling.infrastructure;

import com.eduhub.edu_hub.enrollment.application.EnrollmentFacade;
import com.eduhub.edu_hub.scheduling.application.port.ModuleClassEnrollmentQuery;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FacadeModuleClassEnrollmentQuery implements ModuleClassEnrollmentQuery {
    private final ObjectProvider<EnrollmentFacade.Planning> enrollment;

    @Override
    public Usage summarize(Long moduleClassId, String moduleClassCode) {
        var facade = enrollment.getIfAvailable();
        if (facade == null) throw new BaseException(ErrorCode.MODULE_CLASS_ENROLLMENT_DATA_UNAVAILABLE);
        var usage = facade.summarizeModuleClass(moduleClassId);
        if (usage == null) throw new BaseException(ErrorCode.MODULE_CLASS_ENROLLMENT_DATA_UNAVAILABLE);
        return new Usage(usage.historyComplete(), usage.enrollmentCount(), usage.studentCount(), usage.dataSource());
    }
}
