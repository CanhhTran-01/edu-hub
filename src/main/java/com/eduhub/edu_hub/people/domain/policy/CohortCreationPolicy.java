package com.eduhub.edu_hub.people.domain.policy;

import com.eduhub.edu_hub.people.application.exception.CohortException;
import com.eduhub.edu_hub.people.application.exception.ProfileCreationException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

/** Ghép mã khóa từ STT đã được cấp, không suy STT từ năm học hoặc số lượng bản ghi. */
public final class CohortCreationPolicy {
    public static final int MAXIMUM_COHORT_SEQUENCE = 99;

    private CohortCreationPolicy() {}

    // Service tuần tự hóa việc cấp STT trong cùng transaction; số đã dùng kể cả xóa mềm vẫn được giữ.
    public static String generateCohortCode(int cohortSequence) {
        if (cohortSequence < 1 || cohortSequence > MAXIMUM_COHORT_SEQUENCE)
            throw new ProfileCreationException(ErrorCode.INVALID_STUDENT_CODE_COHORT);
        return "K" + cohortSequence;
    }

    public static void validateYears(Integer startYear, Integer endYear) {
        if (startYear == null || startYear < 1 || startYear > 9998)
            throw new CohortException(ErrorCode.VALIDATION_ERROR, "startYear", "Năm bắt đầu phải từ 1 đến 9998");
        if (endYear == null || endYear < 2 || endYear > 9999)
            throw new CohortException(ErrorCode.VALIDATION_ERROR, "endYear", "Năm kết thúc phải từ 2 đến 9999");
        if (endYear <= startYear)
            throw new CohortException(ErrorCode.VALIDATION_ERROR, "endYear", "Năm kết thúc phải lớn hơn năm bắt đầu");
    }
}
