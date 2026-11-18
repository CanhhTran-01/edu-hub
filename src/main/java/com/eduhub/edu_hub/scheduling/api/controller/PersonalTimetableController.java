package com.eduhub.edu_hub.scheduling.api.controller;

import com.eduhub.edu_hub.scheduling.api.dto.request.WeeklyTimetableRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.WeeklyTimetableResponse;
import com.eduhub.edu_hub.scheduling.application.service.PersonalTimetableQueryService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class PersonalTimetableController {
    private final PersonalTimetableQueryService personalTimetableQueryService;

    @GetMapping("/students/me/semesters/{semesterId}/timetable")
    public ResponseEntity<ApiResponse<WeeklyTimetableResponse>> findStudentTimetable(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable Long semesterId,
            @Valid @ModelAttribute WeeklyTimetableRequest request) {
        return respond(personalTimetableQueryService.findPersonalTimetableForStudent(principal, semesterId, request));
    }

    @GetMapping("/lecturers/me/semesters/{semesterId}/timetable")
    public ResponseEntity<ApiResponse<WeeklyTimetableResponse>> findLecturerTimetable(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable Long semesterId,
            @Valid @ModelAttribute WeeklyTimetableRequest request) {
        return respond(personalTimetableQueryService.findPersonalTimetableForLecturer(principal, semesterId, request));
    }

    @GetMapping("/semesters/{semesterId}/weekly-timetable")
    public ResponseEntity<ApiResponse<WeeklyTimetableResponse>> findSemesterTimetable(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable Long semesterId,
            @Valid @ModelAttribute WeeklyTimetableRequest request) {
        return respond(personalTimetableQueryService.findSemesterTimetable(principal, semesterId, request));
    }

    // Thời khóa biểu là dữ liệu cá nhân và thay đổi thường xuyên (đăng ký/hủy lớp, đổi phòng, đổi lịch),
    // nên cấm mọi lớp cache (trình duyệt, proxy, CDN). Đặt tường minh để không phụ thuộc
    // vào header mặc định của Spring Security.
    private ResponseEntity<ApiResponse<WeeklyTimetableResponse>> respond(WeeklyTimetableResponse timetable) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(timetable));
    }
}
