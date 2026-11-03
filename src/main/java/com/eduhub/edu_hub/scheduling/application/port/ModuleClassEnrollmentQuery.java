package com.eduhub.edu_hub.scheduling.application.port;

/** TODO(ENROLLMENT_INTEGRATION): adapter thật chỉ gọi EnrollmentFacade, không đọc repository liên module. */
public interface ModuleClassEnrollmentQuery {
    /** Đếm mọi bản ghi, kể cả DROPPED và soft delete; không suy ra từ currentSize. */
    Usage summarize(Long moduleClassId, String moduleClassCode);

    record Usage(boolean historyComplete, long enrollmentCount, long studentCount, String dataSource) {}
}
