package com.eduhub.edu_hub.scheduling.application.port;
/** DTO đầu vào từ facade People, Curriculum và lịch sử Enrollment. */
public interface AcademicPlanningDataSource {
    PlanningSnapshot snapshot();
}
