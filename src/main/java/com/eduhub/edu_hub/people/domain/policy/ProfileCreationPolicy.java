package com.eduhub.edu_hub.people.domain.policy;

import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.people.application.exception.ProfileCreationException;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Quy tắc đầu vào dùng chung khi tạo hồ sơ đơn lẻ, không truy vấn dữ liệu hoặc cấp tài khoản. */
public final class ProfileCreationPolicy {
    private static final int MAXIMUM_REASON_LENGTH = 500;

    private ProfileCreationPolicy() {}

    public static Set<Long> requireTeachingSubjectIds(List<Long> teachingSubjectIds) {
        if (teachingSubjectIds == null
                || teachingSubjectIds.isEmpty()
                || teachingSubjectIds.stream().anyMatch(subjectId -> subjectId == null || subjectId <= 0)) {
            throw new ProfileCreationException(
                    ErrorCode.VALIDATION_ERROR, "teachingSubjectIds", "Phải chọn ít nhất một môn với ID hợp lệ");
        }
        return new HashSet<>(teachingSubjectIds);
    }

    public static void validateStudentClassScope(
            AdministrativeClass administrativeClass, Long majorId, Long cohortId, TrainingType trainingType) {
        if (administrativeClass.getMajor() == null
                || !administrativeClass.getMajor().getId().equals(majorId)
                || administrativeClass.getCohort() == null
                || administrativeClass.getCohort().isDeleted()
                || !administrativeClass.getCohort().getId().equals(cohortId)
                || administrativeClass.getTrainingType() != trainingType) {
            throw new ProfileCreationException(ErrorCode.STUDENT_CLASS_MISMATCH);
        }
    }

    public static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > MAXIMUM_REASON_LENGTH) {
            throw new ProfileCreationException(
                    ErrorCode.VALIDATION_ERROR, "reason", "Lý do bắt buộc và tối đa 500 ký tự");
        }
        return reason.trim();
    }

    public static String normalizeRequiredCitizenId(String citizenId) {
        String normalizedCitizenId = normalizeOptionalCitizenId(citizenId);
        if (normalizedCitizenId == null) {
            throw new ProfileCreationException(ErrorCode.VALIDATION_ERROR, "citizenId", "CCCD là thông tin bắt buộc");
        }
        return normalizedCitizenId;
    }

    public static String normalizeOptionalCitizenId(String citizenId) {
        String normalizedCitizenId = TextsNormalizer.normalizeCitizenId(citizenId);
        if (normalizedCitizenId != null && !normalizedCitizenId.matches("[0-9]{12}")) {
            throw new ProfileCreationException(ErrorCode.VALIDATION_ERROR, "citizenId", "CCCD phải gồm đúng 12 chữ số");
        }
        return normalizedCitizenId;
    }
}
