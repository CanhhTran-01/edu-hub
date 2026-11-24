package com.eduhub.edu_hub.scheduling.application.service;

import com.eduhub.edu_hub.scheduling.api.dto.request.ChangeRoomStatusRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.CreateRoomRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.RoomSearchRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.UpdateRoomRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.RoomResponse;
import com.eduhub.edu_hub.scheduling.api.mapper.RoomResponseMapper;
import com.eduhub.edu_hub.scheduling.application.exception.RoomConflictException;
import com.eduhub.edu_hub.scheduling.application.exception.RoomException;
import com.eduhub.edu_hub.scheduling.application.specification.RoomScheduleSpecification;
import com.eduhub.edu_hub.scheduling.application.specification.RoomSpecification;
import com.eduhub.edu_hub.scheduling.domain.enums.RoomStatus;
import com.eduhub.edu_hub.scheduling.domain.model.ModuleClass;
import com.eduhub.edu_hub.scheduling.domain.model.Room;
import com.eduhub.edu_hub.scheduling.domain.policy.RoomPolicy;
import com.eduhub.edu_hub.scheduling.domain.policy.RoomPolicy.NormalizedRoomDetails;
import com.eduhub.edu_hub.scheduling.domain.repository.ClassScheduleRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.RoomRepository;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.mapper.PageMapper;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomApplicationService {
    private final RoomRepository roomRepository;
    private final ClassScheduleRepository classScheduleRepository;
    private final Clock clock;

    public RoomApplicationService(
            RoomRepository roomRepository, ClassScheduleRepository classScheduleRepository, Clock clock) {
        this.roomRepository = roomRepository;
        this.classScheduleRepository = classScheduleRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<RoomResponse> searchRooms(RoomSearchRequest request) {

        var pageable = PageRequest.of(
                request.getPage(), request.getSize(), Sort.by("campus", "buildingCode", "roomNumber", "id"));

        var rooms = roomRepository.findAll(RoomSpecification.search(request), pageable);
        return PageMapper.toPageResponse(rooms.map(RoomResponseMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public RoomResponse findRoomById(Long id) {

        var room = roomRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RoomException(ErrorCode.ROOM_NOT_FOUND));

        return RoomResponseMapper.toResponse(room);
    }

    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {

        // chuẩn hóa dữ liệu request
        var roomDetails = RoomPolicy.normalizeRoomDetails(
                request.getRoomName(),
                request.getCampus(),
                request.getBuildingCode(),
                request.getRoomNumber(),
                request.getCapacity());

        // validate input vừa chuẩn hóa
        requireUnique(roomDetails, null);

        // tạo entity dữ liệu và cập nhật dữ liệu
        var room = Room.builder().status(RoomStatus.ACTIVE).build();
        applyInput(room, roomDetails);
        room.setCreatedAt(now());
        room.setUpdatedAt(room.getCreatedAt());
        var saved = roomRepository.saveAndFlush(room);

        // TODO: có cần ghi log không ?
        return RoomResponseMapper.toResponse(saved);
    }

    @Transactional
    public RoomResponse updateRoom(Long id, UpdateRoomRequest request) {

        // chuẩn hóa dữ liệu request
        var roomDetails = RoomPolicy.normalizeRoomDetails(
                request.getRoomName(),
                request.getCampus(),
                request.getBuildingCode(),
                request.getRoomNumber(),
                request.getCapacity());

        // lôi room cần cập nhật lên
        var room = roomRepository
                .findForUpdateByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RoomException(ErrorCode.ROOM_NOT_FOUND));

        // validate input vừa chuẩn hóa
        requireUnique(roomDetails, id);

        // Giảm sức chứa: không được thấp hơn sĩ số tối đa của bất kỳ lớp nào
        if (roomDetails.capacity() < room.getCapacity()) {
            // truy vấn các lớp học phần không thoả mãn dùng room này trong kỳ chưa kết thúc, trả cho client
            var affected = classesUsingRoom(id).stream()
                    .filter(moduleClass -> moduleClass.getMaxCapacity() > roomDetails.capacity())
                    .toList();
            rejectAffectedClasses(affected, ErrorCode.ROOM_CAPACITY_BELOW_CLASS);
        }

        // cập nhật dữ liệu
        applyInput(room, roomDetails);
        room.setUpdatedAt(now());
        var saved = roomRepository.saveAndFlush(room);

        // TODO: có cần ghi log không ?
        return RoomResponseMapper.toResponse(saved);
    }

    @Transactional
    public RoomResponse changeRoomStatus(Long id, ChangeRoomStatusRequest request) {

        if (request.getStatus() == null) throw new RoomException(ErrorCode.VALIDATION_ERROR);

        // lôi room cần cập nhật lên
        var room = roomRepository
                .findForUpdateByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RoomException(ErrorCode.ROOM_NOT_FOUND));

        if (room.getStatus() == request.getStatus()) return RoomResponseMapper.toResponse(room);

        if (request.getStatus() == RoomStatus.INACTIVE) {
            // Kiểm tra xem phòng có đang được sử dụng không, nếu có thì nhả exception
            rejectAffectedClasses(classesUsingRoom(id), ErrorCode.ROOM_IN_USE);
        }

        // cập nhật dữ liệu
        room.setStatus(request.getStatus());
        room.setUpdatedAt(now());
        var saved = roomRepository.saveAndFlush(room);

        // TODO: có cần ghi log không ?
        return RoomResponseMapper.toResponse(saved);
    }

    @Transactional
    public void deleteRoom(Long id) {

        var room = roomRepository
                .findForUpdateByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RoomException(ErrorCode.ROOM_NOT_FOUND));

        // Kiểm tra xem phòng có đang được sử dụng không, nếu có thì nhả exception
        rejectAffectedClasses(classesUsingRoom(id), ErrorCode.ROOM_IN_USE);

        room.setDeleted(true);
        room.setUpdatedAt(now());
        roomRepository.saveAndFlush(room);

        // TODO: có cần ghi log không ?
    }

    private List<ModuleClass> classesUsingRoom(Long roomId) {
        // Chỉ xét theo kỳ: kỳ chưa kết thúc là còn tính, bỏ qua ngày hết hạn của từng dòng lịch.
        var classesById = new LinkedHashMap<Long, ModuleClass>();
        classScheduleRepository
                .findAll(RoomScheduleSpecification.inUnfinishedSemesters(roomId, LocalDate.now(clock)), Sort.by("id"))
                .forEach(schedule ->
                        classesById.putIfAbsent(schedule.getModuleClass().getId(), schedule.getModuleClass()));

        return List.copyOf(classesById.values());
    }

    private void rejectAffectedClasses(List<ModuleClass> moduleClasses, ErrorCode errorCode) {
        if (!moduleClasses.isEmpty()) {
            throw new RoomConflictException(
                    errorCode,
                    moduleClasses.stream()
                            .map(moduleClass -> new FieldErrorDetail(
                                    "moduleClasses[" + moduleClass.getId() + "]",
                                    moduleClass.getModuleClassCode() + " — "
                                            + moduleClass.getSemester().getSemesterName() + "; sức chứa tối đa: "
                                            + moduleClass.getMaxCapacity()))
                            .toList());
        }
    }

    private void requireUnique(NormalizedRoomDetails roomDetails, Long excludedId) {

        boolean duplicateCode = (excludedId == null)
                ? roomRepository.existsByRoomCode(roomDetails.roomCode())
                : roomRepository.existsByRoomCodeAndIdNot(roomDetails.roomCode(), excludedId);

        if (duplicateCode) {
            throw new RoomException(ErrorCode.DUPLICATE_ROOM_CODE);
        }

        boolean duplicateLocation = (excludedId == null)
                ? roomRepository.existsByCampusAndBuildingCodeAndRoomNumber(
                        roomDetails.campus(), roomDetails.buildingCode(), roomDetails.roomNumber())
                : roomRepository.existsByCampusAndBuildingCodeAndRoomNumberAndIdNot(
                        roomDetails.campus(), roomDetails.buildingCode(), roomDetails.roomNumber(), excludedId);

        if (duplicateLocation) {
            throw new RoomException(ErrorCode.DUPLICATE_ROOM_LOCATION);
        }
    }

    private void applyInput(Room room, NormalizedRoomDetails roomDetails) {
        room.setRoomCode(roomDetails.roomCode());
        room.setRoomName(roomDetails.roomName());
        room.setCampus(roomDetails.campus());
        room.setBuildingCode(roomDetails.buildingCode());
        room.setRoomNumber(roomDetails.roomNumber());
        room.setCapacity(roomDetails.capacity());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
