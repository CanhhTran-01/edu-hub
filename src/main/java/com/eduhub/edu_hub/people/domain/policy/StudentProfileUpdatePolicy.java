package com.eduhub.edu_hub.people.domain.policy;

import com.eduhub.edu_hub.people.application.exception.StudentProfileException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import java.time.LocalDate;

/** Chuẩn hóa và kiểm tra thông tin hồ sơ từ đầu vào; không tải dữ liệu hoặc kiểm tra quyền người gọi. */
public final class StudentProfileUpdatePolicy {
    private static final int MAXIMUM_PROFILE_TEXT_LENGTH = 255;
    private static final int MAXIMUM_UPDATE_REASON_LENGTH = 500;
    private static final String CITIZEN_ID_PATTERN = "[0-9]{12}";

    private StudentProfileUpdatePolicy() {}

    public static String normalizeFullName(String fullName) {
        if (fullName == null) return null;
        if (fullName.isBlank() || fullName.length() > MAXIMUM_PROFILE_TEXT_LENGTH) {
            throw new StudentProfileException(
                    ErrorCode.VALIDATION_ERROR, "fullName", "Họ tên không được để trống và tối đa 255 ký tự");
        }
        return fullName.trim();
    }

    public static String normalizePhone(String phone) {
        if (phone == null) return null;
        if (phone.length() > MAXIMUM_PROFILE_TEXT_LENGTH) {
            throw new StudentProfileException(ErrorCode.VALIDATION_ERROR, "phone", "Số điện thoại tối đa 255 ký tự");
        }
        // Giữ cùng quy tắc với hồ sơ cá nhân: chuỗi trắng thành chuỗi rỗng, chưa ràng buộc định dạng số.
        return phone.trim();
    }

    public static String normalizeCitizenId(String citizenId) {
        if (citizenId == null) return null;
        String normalizedCitizenId = TextsNormalizer.normalizeCitizenId(citizenId);
        if (normalizedCitizenId == null || !normalizedCitizenId.matches(CITIZEN_ID_PATTERN)) {
            throw new StudentProfileException(ErrorCode.VALIDATION_ERROR, "citizenId", "CCCD phải gồm đúng 12 chữ số");
        }
        return normalizedCitizenId;
    }

    public static void validateDateOfBirth(LocalDate dateOfBirth, LocalDate currentDate) {
        if (dateOfBirth != null && !dateOfBirth.isBefore(currentDate)) {
            throw new StudentProfileException(
                    ErrorCode.VALIDATION_ERROR, "dateOfBirth", "Ngày sinh phải là ngày trong quá khứ");
        }
    }

    public static String normalizeUpdateReason(String updateReason) {
        if (updateReason == null) return null;
        if (updateReason.length() > MAXIMUM_UPDATE_REASON_LENGTH) {
            throw new StudentProfileException(
                    ErrorCode.VALIDATION_ERROR, "statusReason", "Lý do cập nhật tối đa 500 ký tự");
        }
        return updateReason.trim();
    }
}
