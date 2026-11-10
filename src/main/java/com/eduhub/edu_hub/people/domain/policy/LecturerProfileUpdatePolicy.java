package com.eduhub.edu_hub.people.domain.policy;

import com.eduhub.edu_hub.people.application.exception.LecturerProfileException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Chuẩn hóa và kiểm tra đầu vào cập nhật giảng viên; không tải dữ liệu hoặc dựng DTO HTTP. */
public final class LecturerProfileUpdatePolicy {
    private static final int MAXIMUM_PROFILE_TEXT_LENGTH = 255;
    private static final int MAXIMUM_UPDATE_REASON_LENGTH = 500;

    private LecturerProfileUpdatePolicy() {}

    public static String normalizeFullName(String fullName) {
        if (fullName == null) return null;
        if (fullName.isBlank() || fullName.length() > MAXIMUM_PROFILE_TEXT_LENGTH) {
            throw new LecturerProfileException(
                    ErrorCode.VALIDATION_ERROR, "fullName", "Họ tên không được để trống và tối đa 255 ký tự");
        }
        return fullName.trim();
    }

    public static String normalizePhone(String phone) {
        if (phone == null) return null;
        if (phone.length() > MAXIMUM_PROFILE_TEXT_LENGTH) {
            throw new LecturerProfileException(ErrorCode.VALIDATION_ERROR, "phone", "Số điện thoại tối đa 255 ký tự");
        }
        return phone.trim();
    }

    public static String normalizeCitizenId(String citizenId) {
        if (citizenId == null) return null;
        String normalizedCitizenId = TextsNormalizer.normalizeCitizenId(citizenId);
        if (normalizedCitizenId == null || !normalizedCitizenId.matches("[0-9]{12}")) {
            throw new LecturerProfileException(ErrorCode.VALIDATION_ERROR, "citizenId", "CCCD phải gồm đúng 12 chữ số");
        }
        return normalizedCitizenId;
    }

    public static void validateDateOfBirth(LocalDate dateOfBirth, LocalDate currentDate) {
        if (dateOfBirth != null && !dateOfBirth.isBefore(currentDate)) {
            throw new LecturerProfileException(
                    ErrorCode.VALIDATION_ERROR, "dateOfBirth", "Ngày sinh phải là ngày trong quá khứ");
        }
    }

    public static String normalizeUpdateReason(String updateReason) {
        if (updateReason == null) return null;
        if (updateReason.length() > MAXIMUM_UPDATE_REASON_LENGTH) {
            throw new LecturerProfileException(ErrorCode.VALIDATION_ERROR, "reason", "Lý do cập nhật tối đa 500 ký tự");
        }
        return updateReason.trim();
    }

    public static Set<Long> normalizeTeachingSubjectIds(List<Long> teachingSubjectIds) {
        if (teachingSubjectIds == null) return null;
        if (teachingSubjectIds.isEmpty()
                || teachingSubjectIds.stream().anyMatch(subjectId -> subjectId == null || subjectId <= 0)) {
            throw new LecturerProfileException(
                    ErrorCode.VALIDATION_ERROR,
                    "teachingSubjectIds",
                    "Phải chọn ít nhất một môn với ID dương và không null");
        }
        return new TreeSet<>(teachingSubjectIds);
    }
}
