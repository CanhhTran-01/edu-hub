package com.eduhub.edu_hub.scheduling.domain.policy;

import com.eduhub.edu_hub.scheduling.application.exception.RoomException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RoomPolicy {
    public static final String CODE_PATTERN = "[A-Z0-9]+-[A-Z0-9]+-[0-9]+";
    public static final String CODE_FORMAT_MESSAGE =
            "Mã phòng phải theo định dạng <cơ sở>-<tòa nhà>-<số phòng>, ví dụ CS1-TA1-102";

    private static final Pattern ROOM_CODE = Pattern.compile(CODE_PATTERN);

    public static NormalizedRoomDetails normalizeRoomDetails(
            String roomName,
            String campusCodeInput,
            String buildingCodeInput,
            String roomNumberInput,
            Integer capacity) {

        // check sức chứa
        if (capacity == null || capacity < 1) {
            throw new RoomException(ErrorCode.VALIDATION_ERROR);
        }

        String roomNumber = normalizeIdentifier(roomNumberInput, 50);
        String buildingCode = normalizeIdentifier(buildingCodeInput, 50);
        // lấy mã tòa nhà
        String campusCode = normalizeIdentifier(campusCodeInput, 100);
        // BE ghép mã từ vị trí đã chuẩn hóa để cùng số phòng ở các cơ sở khác nhau có mã riêng.
        String roomCode = generateRoomCode(campusCode, buildingCode, roomNumber);

        return new NormalizedRoomDetails(
                roomCode, requireText(roomName, 255), campusCode, buildingCode, roomNumber, capacity);
    }

    public static String generateRoomCode(String campus, String buildingCode, String roomNumber) {
        String code = normalizeIdentifier(campus, 100) + "-" + normalizeIdentifier(buildingCode, 50) + "-"
                + normalizeIdentifier(roomNumber, 50);
        if (code.length() > 50 || !ROOM_CODE.matcher(code).matches())
            throw new RoomException(ErrorCode.INVALID_ROOM_CODE);
        return code;
    }

    private static String normalizeIdentifier(String rawText, int maxLength) {
        String normalized = requireText(rawText, maxLength).toUpperCase(Locale.ROOT);
        if (normalized.length() > maxLength) throw new RoomException(ErrorCode.VALIDATION_ERROR);
        return normalized;
    }

    private static String requireText(String rawText, int maxLength) {
        if (rawText == null || rawText.isBlank() || rawText.length() > maxLength)
            throw new RoomException(ErrorCode.VALIDATION_ERROR);
        String normalized = rawText.trim();
        if (normalized.isBlank()) throw new RoomException(ErrorCode.VALIDATION_ERROR);
        return normalized;
    }

    public record NormalizedRoomDetails(
            String roomCode, String roomName, String campus, String buildingCode, String roomNumber, int capacity) {}
}
