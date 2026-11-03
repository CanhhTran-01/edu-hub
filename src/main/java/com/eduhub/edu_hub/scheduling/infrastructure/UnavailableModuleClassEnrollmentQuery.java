package com.eduhub.edu_hub.scheduling.infrastructure;

import com.eduhub.edu_hub.scheduling.application.port.ModuleClassEnrollmentQuery;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev & !test")
public class UnavailableModuleClassEnrollmentQuery implements ModuleClassEnrollmentQuery {
    @Override
    public Usage summarize(Long moduleClassId, String moduleClassCode) {
        // TODO(ENROLLMENT_INTEGRATION): cần nguồn đếm cả đăng ký đã hủy/ẩn, dùng ID lớp thật.
        throw new BaseException(ErrorCode.MODULE_CLASS_ENROLLMENT_DATA_UNAVAILABLE);
    }
}
