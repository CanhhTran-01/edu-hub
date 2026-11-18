package com.eduhub.edu_hub.scheduling.api.controller;

import com.eduhub.edu_hub.scheduling.api.dto.request.ChangeRoomStatusRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.CreateRoomRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.RoomSearchRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.UpdateRoomRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.RoomResponse;
import com.eduhub.edu_hub.scheduling.application.service.RoomApplicationService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final ApiResponseFactory apiResponseFactory;
    private final RoomApplicationService roomApplicationService;

    @GetMapping
    public ApiResponse<PageResponse<RoomResponse>> search(@Valid @ModelAttribute RoomSearchRequest request) {
        var rooms = roomApplicationService.searchRooms(request);
        return apiResponseFactory
                .<PageResponse<RoomResponse>>builder()
                .success(true)
                .message(rooms.getContent().isEmpty() ? "Không tìm thấy kết quả" : "Success")
                .data(rooms)
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<RoomResponse> findById(@PathVariable Long id) {
        return apiResponseFactory.success(roomApplicationService.findRoomById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> create(@Valid @RequestBody CreateRoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apiResponseFactory
                        .<RoomResponse>builder()
                        .success(true)
                        .message("Thêm phòng học thành công")
                        .data(roomApplicationService.createRoom(request))
                        .build());
    }

    @PutMapping("/{id}")
    public ApiResponse<RoomResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateRoomRequest request) {
        return apiResponseFactory
                .<RoomResponse>builder()
                .success(true)
                .message("Cập nhật phòng học thành công")
                .data(roomApplicationService.updateRoom(id, request))
                .build();
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<RoomResponse> changeStatus(
            @PathVariable Long id, @Valid @RequestBody ChangeRoomStatusRequest request) {
        return apiResponseFactory
                .<RoomResponse>builder()
                .success(true)
                .message("Cập nhật trạng thái phòng học thành công")
                .data(roomApplicationService.changeRoomStatus(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roomApplicationService.deleteRoom(id);
        return apiResponseFactory.successWithMessage("Xóa phòng học thành công");
    }
}
