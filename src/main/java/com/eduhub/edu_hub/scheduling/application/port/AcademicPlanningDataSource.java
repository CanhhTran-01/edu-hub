package com.eduhub.edu_hub.scheduling.application.port;
/** Nguồn DTO đầu vào. Adapter thật sau này chỉ gọi PeopleFacade/EnrollmentFacade. */
public interface AcademicPlanningDataSource {
    PlanningSnapshot snapshot();
}
