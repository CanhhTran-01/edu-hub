package com.eduhub.edu_hub.people.domain.policy;

import com.eduhub.edu_hub.people.application.exception.ProfileCreationException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import java.util.Locale;

/** Định dạng MSGV từ mã khoa, năm tuyển dụng và STT đã được cấp trong transaction. */
public final class LecturerCodePolicy {
    public static final int MAXIMUM_LECTURER_SEQUENCE = 999;

    private LecturerCodePolicy() {}

    public static void validateCodeScope(String facultyCode, Integer recruitmentYear) {
        if (facultyCode == null || !facultyCode.matches("[A-Z]+")) {
            throw new ProfileCreationException(
                    ErrorCode.INVALID_LECTURER_CODE_FACULTY,
                    "facultyId",
                    "Khoa cần có mã định danh gồm chữ cái in hoa trước khi cấp MSGV");
        }
        if (recruitmentYear == null || recruitmentYear < 1000 || recruitmentYear > 9999) {
            throw new ProfileCreationException(
                    ErrorCode.VALIDATION_ERROR, "recruitmentYear", "Năm tuyển dụng phải gồm bốn chữ số");
        }
    }

    public static void validateCodeFormat(String codeFormat) {
        if (codeFormat == null) throw new ProfileCreationException(ErrorCode.VALIDATION_ERROR);
        String remainingFormat = codeFormat;
        // Mỗi thành phần định danh phải xuất hiện đúng một lần, như cấu hình định dạng MSSV.
        for (String token : List.of("[MaKhoa]", "[NamTuyenDung]", "[SttGV]")) {
            if (!remainingFormat.contains(token)
                    || remainingFormat.indexOf(token) != remainingFormat.lastIndexOf(token)) {
                throw new ProfileCreationException(ErrorCode.VALIDATION_ERROR);
            }
            remainingFormat = remainingFormat.replace(token, "");
        }
        if (!remainingFormat.matches("[A-Za-z0-9_-]*")) throw new ProfileCreationException(ErrorCode.VALIDATION_ERROR);
    }

    public static String generateCode(
            String facultyCode, Integer recruitmentYear, int lecturerSequence, String codeFormat) {
        validateCodeScope(facultyCode, recruitmentYear);
        validateCodeFormat(codeFormat);
        if (lecturerSequence < 1 || lecturerSequence > MAXIMUM_LECTURER_SEQUENCE) {
            throw new ProfileCreationException(ErrorCode.LECTURER_CODE_EXHAUSTED);
        }
        return codeFormat
                .replace("[MaKhoa]", facultyCode)
                .replace("[NamTuyenDung]", String.format(Locale.ROOT, "%02d", recruitmentYear % 100))
                .replace("[SttGV]", String.format(Locale.ROOT, "%03d", lecturerSequence));
    }
}
