package com.eduhub.edu_hub.scheduling.infrastructure;

import com.eduhub.edu_hub.scheduling.application.port.*;
import com.eduhub.edu_hub.shared.exception.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev & !test")
public class UnavailableAcademicPlanningDataSource implements AcademicPlanningDataSource {
    @Override
    public PlanningSnapshot snapshot() {
        // TODO(PEOPLE_ENROLLMENT_INTEGRATION): thay bằng adapter Facade thật.
        // Không dùng fixture hoặc coi thiếu lịch sử là chưa từng đăng ký.
        throw new BaseException(ErrorCode.PLANNING_DATA_UNAVAILABLE);
    }
}
