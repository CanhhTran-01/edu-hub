package com.eduhub.edu_hub.scheduling.application;

import com.eduhub.edu_hub.scheduling.api.dto.*;
import com.eduhub.edu_hub.scheduling.application.exception.RoomConflictException;
import com.eduhub.edu_hub.scheduling.application.specification.RoomScheduleSpecification;
import com.eduhub.edu_hub.scheduling.application.specification.RoomSpecification;
import com.eduhub.edu_hub.scheduling.domain.enums.RoomStatus;
import com.eduhub.edu_hub.scheduling.domain.model.ModuleClass;
import com.eduhub.edu_hub.scheduling.domain.model.Room;
import com.eduhub.edu_hub.scheduling.domain.policy.RoomPolicy;
import com.eduhub.edu_hub.scheduling.domain.repository.ClassScheduleRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.RoomRepository;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.mapper.PageMapper;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomApplicationService {
    private final RoomRepository roomRepository;
    private final ClassScheduleRepository schedules;
    private final Clock clock;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Pattern ROOM_CODE = Pattern.compile(RoomPolicy.CODE_PATTERN);

    @Autowired
    public RoomApplicationService(RoomRepository roomRepository, ClassScheduleRepository schedules) {
        this(roomRepository, schedules, Clock.system(BUSINESS_ZONE));
    }

    RoomApplicationService(RoomRepository roomRepository, ClassScheduleRepository schedules, Clock clock) {
        this.roomRepository = roomRepository;
        this.schedules = schedules;
        this.clock = clock.withZone(BUSINESS_ZONE);
    }

    @Transactional(readOnly = true)
    public PageResponse<RoomResponse> search(RoomSearchRequest request) {

        var pageable = PageRequest.of(
                request.getPage(), request.getSize(), Sort.by("campus", "buildingCode", "roomNumber", "id"));

        var rooms = roomRepository.findAll(RoomSpecification.search(request), pageable);
        return PageMapper.toPageResponse(rooms.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public RoomResponse findById(Long id) {

        var room = roomRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.ROOM_NOT_FOUND));

        return toResponse(room);
    }

    @Transactional
    public RoomResponse create(CreateRoomRequest request) {

        // chuẩn hóa dữ liệu request
        var input = normalize(
                request.getRoomCode(),
                request.getRoomName(),
                request.getCampus(),
                request.getBuildingCode(),
                request.getRoomNumber(),
                request.getCapacity());

        // validate input vừa chuẩn hóa
        requireUnique(input, null);

        // tạo entity dữ liệu và cập nhật dữ liệu
        var room = Room.builder().status(RoomStatus.ACTIVE).build();
        applyInput(room, input);
        room.setCreatedAt(now());
        room.setUpdatedAt(room.getCreatedAt());
        var saved = roomRepository.saveAndFlush(room);

        // TODO: có cần ghi log không ?
        return toResponse(saved);
    }

    @Transactional
    public RoomResponse update(Long id, UpdateRoomRequest request) {

        // chuẩn hóa dữ liệu request
        var input = normalize(
                request.getRoomCode(),
                request.getRoomName(),
                request.getCampus(),
                request.getBuildingCode(),
                request.getRoomNumber(),
                request.getCapacity());

        // lôi room cần cập nhật lên
        var room = roomRepository
                .findForUpdateByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.ROOM_NOT_FOUND));

        // validate input vừa chuẩn hóa
        requireUnique(input, id);

        // Giảm sức chứa: không được thấp hơn sĩ số tối đa của bất kỳ lớp nào
        if (input.capacity() < room.getCapacity()) {
            // truy vấn các lớp học phần không thoả mãn dùng room này trong kỳ chưa kết thúc, trả cho client
            var affected = classesUsingRoom(id).stream()
                    .filter(item -> item.getMaxCapacity() > input.capacity())
                    .toList();
            rejectAffectedClasses(affected, ErrorCode.ROOM_CAPACITY_BELOW_CLASS);
        }

        // cập nhật dữ liệu
        applyInput(room, input);
        room.setUpdatedAt(now());
        var saved = roomRepository.saveAndFlush(room);

        // TODO: có cần ghi log không ?
        return toResponse(saved);
    }

    @Transactional
    public RoomResponse changeStatus(Long id, ChangeRoomStatusRequest request) {

        if (request.getStatus() == null) throw new BaseException(ErrorCode.VALIDATION_ERROR);

        // lôi room cần cập nhật lên
        var room = roomRepository
                .findForUpdateByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.ROOM_NOT_FOUND));

        if (room.getStatus() == request.getStatus()) return toResponse(room);

        if (request.getStatus() == RoomStatus.INACTIVE) {
            // Kiểm tra xem phòng có đang được sử dụng không, nếu có thì nhả exception
            rejectAffectedClasses(classesUsingRoom(id), ErrorCode.ROOM_IN_USE);
        }

        // cập nhật dữ liệu
        room.setStatus(request.getStatus());
        room.setUpdatedAt(now());
        var saved = roomRepository.saveAndFlush(room);

        // TODO: có cần ghi log không ?
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {

        var room = roomRepository
                .findForUpdateByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.ROOM_NOT_FOUND));
        ;

        // Kiểm tra xem phòng có đang được sử dụng không, nếu có thì nhả exception
        rejectAffectedClasses(classesUsingRoom(id), ErrorCode.ROOM_IN_USE);

        room.setDeleted(true);
        room.setUpdatedAt(now());
        roomRepository.saveAndFlush(room);

        // TODO: có cần ghi log không ?
    }

    private List<ModuleClass> classesUsingRoom(Long roomId) {
        // Chỉ xét theo kỳ: kỳ chưa kết thúc là còn tính, bỏ qua ngày hết hạn của từng dòng lịch.
        var result = new LinkedHashMap<Long, ModuleClass>();
        schedules
                .findAll(RoomScheduleSpecification.inUnfinishedSemesters(roomId, LocalDate.now(clock)), Sort.by("id"))
                .forEach(
                        schedule -> result.putIfAbsent(schedule.getModuleClass().getId(), schedule.getModuleClass()));

        return List.copyOf(result.values());
    }

    private void rejectAffectedClasses(List<ModuleClass> classes, ErrorCode error) {
        if (!classes.isEmpty()) {
            throw new RoomConflictException(
                    error,
                    classes.stream()
                            .map(item -> new FieldErrorDetail(
                                    "moduleClasses[" + item.getId() + "]",
                                    item.getModuleClassCode() + " — "
                                            + item.getSemester().getSemesterName() + "; sức chứa tối đa: "
                                            + item.getMaxCapacity()))
                            .toList());
        }
    }

    private void requireUnique(RoomInput input, Long excludedId) {

        boolean duplicateCode = (excludedId == null)
                ? roomRepository.existsByRoomCode(input.code())
                : roomRepository.existsByRoomCodeAndIdNot(input.code(), excludedId);

        if (duplicateCode) {
            throw new BaseException(ErrorCode.DUPLICATE_ROOM_CODE);
        }

        boolean duplicateLocation = (excludedId == null)
                ? roomRepository.existsByCampusAndBuildingCodeAndRoomNumber(
                        input.campus(), input.building(), input.number())
                : roomRepository.existsByCampusAndBuildingCodeAndRoomNumberAndIdNot(
                        input.campus(), input.building(), input.number(), excludedId);

        if (duplicateLocation) {
            throw new BaseException(ErrorCode.DUPLICATE_ROOM_LOCATION);
        }
    }

    private RoomInput normalize(
            String rawCode, String name, String campus, String building, String number, Integer capacity) {

        // check code có khớp PATTERN không
        String code = normalizeIdentifier(rawCode, 50);
        if (!ROOM_CODE.matcher(code).matches()) {
            throw new BaseException(ErrorCode.INVALID_ROOM_CODE);
        }

        // check sức chứa
        if (capacity == null || capacity < 1) {
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        }

        int separator = code.indexOf('-');
        String codeNumber = code.substring(0, separator);
        String codeBuilding = code.substring(separator + 1);
        String roomNumber = number == null ? codeNumber : normalizeIdentifier(number, 50);
        String buildingCode = normalizeIdentifier(building, 50);
        var errors = new ArrayList<FieldErrorDetail>();
        if (!roomNumber.equals(codeNumber)) {
            errors.add(new FieldErrorDetail("roomNumber", "Số phòng phải khớp phần trước dấu - trong mã phòng"));
        }
        if (!buildingCode.equals(codeBuilding)) {
            errors.add(new FieldErrorDetail("buildingCode", "Tòa nhà phải khớp phần sau dấu - trong mã phòng"));
        }
        if (!errors.isEmpty()) {
            throw new RoomConflictException(ErrorCode.VALIDATION_ERROR, errors);
        }

        // lấy mã tòa nhà
        String campusCode = (campus == null || campus.isBlank()) ? "" : normalizeIdentifier(campus, 100);

        return new RoomInput(code, requireText(name, 255), campusCode, buildingCode, roomNumber, capacity);
    }

    private String normalizeIdentifier(String value, int maxLength) {
        String normalized = requireText(value, maxLength).toUpperCase(Locale.ROOT);
        if (normalized.length() > maxLength) throw new BaseException(ErrorCode.VALIDATION_ERROR);
        return normalized;
    }

    private String requireText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength)
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        String normalized = value.trim();
        if (normalized.isBlank()) throw new BaseException(ErrorCode.VALIDATION_ERROR);
        return normalized;
    }

    private void applyInput(Room room, RoomInput input) {
        room.setRoomCode(input.code());
        room.setRoomName(input.name());
        room.setCampus(input.campus());
        room.setBuildingCode(input.building());
        room.setRoomNumber(input.number());
        room.setCapacity(input.capacity());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private RoomResponse toResponse(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .roomCode(room.getRoomCode())
                .roomName(room.getRoomName())
                .campus(room.getCampus())
                .buildingCode(room.getBuildingCode())
                .roomNumber(room.getRoomNumber())
                .capacity(room.getCapacity())
                .status(room.getStatus())
                .build();
    }

    private record RoomInput(String code, String name, String campus, String building, String number, int capacity) {}
}
