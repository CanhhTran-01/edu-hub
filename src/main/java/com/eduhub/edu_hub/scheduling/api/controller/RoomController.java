package com.eduhub.edu_hub.scheduling.api.controller;

import com.eduhub.edu_hub.scheduling.api.dto.*;
import com.eduhub.edu_hub.scheduling.application.RoomApplicationService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
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
    private final RoomApplicationService service;

    @GetMapping
    public ApiResponse<PageResponse<RoomResponse>> search(@Valid @ModelAttribute RoomSearchRequest request) {
        var rooms = service.search(request);
        return ApiResponse.<PageResponse<RoomResponse>>builder()
                .success(true)
                .message(rooms.getContent().isEmpty() ? "Không tìm thấy kết quả" : "Success")
                .data(rooms)
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<RoomResponse> findById(@PathVariable Long id) {
        return ApiResponse.success(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> create(@Valid @RequestBody CreateRoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<RoomResponse>builder()
                        .success(true)
                        .message("Thêm phòng học thành công")
                        .data(service.create(request))
                        .build());
    }

    @PutMapping("/{id}")
    public ApiResponse<RoomResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateRoomRequest request) {
        return ApiResponse.<RoomResponse>builder()
                .success(true)
                .message("Cập nhật phòng học thành công")
                .data(service.update(id, request))
                .build();
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<RoomResponse> changeStatus(
            @PathVariable Long id, @Valid @RequestBody ChangeRoomStatusRequest request) {
        return ApiResponse.<RoomResponse>builder()
                .success(true)
                .message("Cập nhật trạng thái phòng học thành công")
                .data(service.changeStatus(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.successWithMessage("Xóa phòng học thành công");
    }
}
