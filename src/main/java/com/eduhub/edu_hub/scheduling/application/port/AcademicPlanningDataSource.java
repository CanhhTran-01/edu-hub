package com.eduhub.edu_hub.scheduling.application.port;

import com.eduhub.edu_hub.scheduling.domain.model.AcademicPlanningSnapshot;

/** DTO đầu vào từ facade People, Curriculum và lịch sử Enrollment. */
public interface AcademicPlanningDataSource {
    AcademicPlanningSnapshot snapshot();
}
