package com.eduhub.edu_hub.scheduling.api.controller;

import com.eduhub.edu_hub.scheduling.api.dto.request.*;
import com.eduhub.edu_hub.scheduling.api.dto.response.*;
import com.eduhub.edu_hub.scheduling.application.service.TimetablePlanApplicationService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TimetableController {
    private final ApiResponseFactory apiResponseFactory;
    private final TimetablePlanApplicationService timetablePlanService;

    // BƯỚC 1: Tạo plan nháp và bắt đầu chạy solver ở nền.
    // Trả 202 (đã nhận, chưa xong); plan lúc này ở trạng thái QUEUED.
    // Body chứa pinnedModuleClassIds: các lớp muốn giữ cố định, solver không được đổi.
    @PostMapping("/semesters/{semesterId}/timetable-plans")
    public ResponseEntity<ApiResponse<TimetablePlanResponse>> createPlan(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable Long semesterId,
            @Valid @RequestBody CreateTimetablePlanRequest request) {
        return ResponseEntity.accepted()
                .body(apiResponseFactory.success(timetablePlanService.createPlan(principal, semesterId, request)));
    }

    // BƯỚC 2: Xem trạng thái và kết quả hiện tại của plan.
    // Client gọi lặp (polling) cho tới khi state không còn QUEUED/RUNNING/STOP_REQUESTED.
    // Kết quả tốt nhất được solver cập nhật vào DB khoảng mỗi giây.
    @GetMapping("/timetable-plans/{planId}")
    public ApiResponse<TimetablePlanResponse> findPlan(
            @AuthenticationPrincipal Jwt principal, @PathVariable Long planId) {
        return apiResponseFactory.success(timetablePlanService.findPlan(principal, planId));
    }

    // BƯỚC 2b (tùy chọn): Yêu cầu dừng solver sớm.
    // Plan chuyển sang STOP_REQUESTED, sau đó thành STOPPED và giữ lời giải tốt nhất tới lúc đó.
    @PostMapping("/timetable-plans/{planId}/stop")
    public ApiResponse<TimetablePlanResponse> stopPlan(
            @AuthenticationPrincipal Jwt principal, @PathVariable Long planId) {
        return apiResponseFactory.success(timetablePlanService.stopPlan(principal, planId));
    }

    // BƯỚC 3: Áp dụng plan nháp thành lịch chính thức.
    // Chỉ làm được khi plan ở FEASIBLE/STOPPED, chưa hết hạn, dữ liệu đầu vào chưa đổi.
    // Thành công thì plan chuyển sang APPLIED.
    @PostMapping("/timetable-plans/{planId}/apply")
    public ApiResponse<TimetablePlanResponse> applyPlan(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable Long planId,
            @Valid @RequestBody ApplyTimetablePlanRequest request) {
        return apiResponseFactory.success(timetablePlanService.applyPlan(principal, planId, request));
    }

    // BƯỚC 4: Xem lịch chính thức của học kỳ (khác với plan nháp ở trên).
    @GetMapping("/semesters/{semesterId}/timetable")
    public ApiResponse<TimetableResponse> findTimetable(
            @AuthenticationPrincipal Jwt principal, @PathVariable Long semesterId) {
        return apiResponseFactory.success(timetablePlanService.findTimetable(principal, semesterId));
    }
}
