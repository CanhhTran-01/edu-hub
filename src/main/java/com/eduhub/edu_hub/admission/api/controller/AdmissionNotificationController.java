package com.eduhub.edu_hub.admission.api.controller;

import com.eduhub.edu_hub.admission.application.service.AdmissionNotificationQueryService;
import com.eduhub.edu_hub.notification.api.dto.request.SearchAdmissionNotificationsRequest;
import com.eduhub.edu_hub.notification.api.dto.response.*;
import com.eduhub.edu_hub.shared.common.*;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Tab tiến trình mail dùng URL gắn planId; các API này chỉ đọc, mở lại tab không kích hoạt gửi lại. */
@RestController
@RequestMapping("/api/v1/student-admissions/plans/{planId}/notifications")
@RequiredArgsConstructor
public class AdmissionNotificationController {
    private final ApiResponseFactory apiResponseFactory;
    private final AdmissionNotificationQueryService notificationQueryService;

    // FE theo dõi thường xuyên bằng progress để không tải toàn bộ mỗi lần.
    @GetMapping
    public ApiResponse<List<AdmissionNotificationResponse>> listPlanNotifications(@PathVariable Long planId) {
        return apiResponseFactory.success(notificationQueryService.listPlanNotifications(planId));
    }

    // FE có thể poll 3–5 giây/lần; dừng khi isComplete, hiển thị tạm ngừng khi dispatchEnabled=false.
    // Gửi mail lỗi không hoàn tác hồ sơ ENROLLED. Đóng tab chỉ dừng việc đọc tiến trình trên FE.
    @GetMapping("/progress")
    public ResponseEntity<ApiResponse<AdmissionNotificationProgressResponse>> getProgress(@PathVariable Long planId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(apiResponseFactory.success(notificationQueryService.getProgress(planId)));
    }

    // Bộ lọc được áp dụng trước phân trang. Chỉ tải thông tin nhận diện cho trang đang hiển thị.
    @GetMapping("/details")
    public ResponseEntity<ApiResponse<PageResponse<AdmissionNotificationDetailResponse>>> searchNotifications(
            @PathVariable Long planId, @Valid @ModelAttribute SearchAdmissionNotificationsRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(apiResponseFactory.success(notificationQueryService.searchNotifications(planId, request)));
    }
}
