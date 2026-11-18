package com.eduhub.edu_hub.scheduling.domain.policy;

import com.eduhub.edu_hub.scheduling.application.exception.RoomConflictException;
import com.eduhub.edu_hub.scheduling.application.exception.RoomException;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RoomPolicy {
    public static final String CODE_PATTERN = "[0-9]+-[A-Za-z0-9]+";
    public static final String CODE_FORMAT_MESSAGE = "Mã phòng phải theo định dạng <số phòng>-<tòa nhà>, ví dụ 102-TA1";

    private static final Pattern ROOM_CODE = Pattern.compile(CODE_PATTERN);

    public static NormalizedRoomDetails normalizeRoomDetails(
            String rawRoomCode,
            String roomName,
            String campusCodeInput,
            String buildingCodeInput,
            String roomNumberInput,
            Integer capacity) {

        // check code có khớp PATTERN không
        String roomCode = normalizeIdentifier(rawRoomCode, 50);
        if (!ROOM_CODE.matcher(roomCode).matches()) {
            throw new RoomException(ErrorCode.INVALID_ROOM_CODE);
        }

        // check sức chứa
        if (capacity == null || capacity < 1) {
            throw new RoomException(ErrorCode.VALIDATION_ERROR);
        }

        int separator = roomCode.indexOf('-');
        String roomNumberFromCode = roomCode.substring(0, separator);
        String buildingFromCode = roomCode.substring(separator + 1);
        String roomNumber = roomNumberInput == null ? roomNumberFromCode : normalizeIdentifier(roomNumberInput, 50);
        String buildingCode = normalizeIdentifier(buildingCodeInput, 50);
        var fieldErrors = new ArrayList<FieldErrorDetail>();
        if (!roomNumber.equals(roomNumberFromCode)) {
            fieldErrors.add(new FieldErrorDetail("roomNumber", "Số phòng phải khớp phần trước dấu - trong mã phòng"));
        }
        if (!buildingCode.equals(buildingFromCode)) {
            fieldErrors.add(new FieldErrorDetail("buildingCode", "Tòa nhà phải khớp phần sau dấu - trong mã phòng"));
        }
        if (!fieldErrors.isEmpty()) {
            throw new RoomConflictException(ErrorCode.VALIDATION_ERROR, fieldErrors);
        }

        // lấy mã tòa nhà
        String campusCode =
                (campusCodeInput == null || campusCodeInput.isBlank()) ? "" : normalizeIdentifier(campusCodeInput, 100);

        return new NormalizedRoomDetails(
                roomCode, requireText(roomName, 255), campusCode, buildingCode, roomNumber, capacity);
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
