package com.eduhub.edu_hub.scheduling.infrastructure;

import com.eduhub.edu_hub.enrollment.application.EnrollmentFacade;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassException;
import com.eduhub.edu_hub.scheduling.application.port.ModuleClassEnrollmentQuery;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FacadeModuleClassEnrollmentQuery implements ModuleClassEnrollmentQuery {
    private final ObjectProvider<EnrollmentFacade.Planning> enrollmentFacadeProvider;

    @Override
    public Usage summarize(Long moduleClassId, String moduleClassCode) {
        var enrollmentFacade = enrollmentFacadeProvider.getIfAvailable();
        if (enrollmentFacade == null)
            throw new ModuleClassException(ErrorCode.MODULE_CLASS_ENROLLMENT_DATA_UNAVAILABLE);
        var enrollmentUsage = enrollmentFacade.summarizeModuleClass(moduleClassId);
        if (enrollmentUsage == null) throw new ModuleClassException(ErrorCode.MODULE_CLASS_ENROLLMENT_DATA_UNAVAILABLE);
        return new Usage(
                enrollmentUsage.historyComplete(),
                enrollmentUsage.enrollmentCount(),
                enrollmentUsage.studentCount(),
                enrollmentUsage.dataSource());
    }
}
