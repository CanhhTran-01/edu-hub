package com.eduhub.edu_hub.enrollment.api.controller;

import com.eduhub.edu_hub.enrollment.api.dto.request.*;
import com.eduhub.edu_hub.enrollment.api.dto.response.CourseGradeResponse;
import com.eduhub.edu_hub.enrollment.application.service.CourseGradeService;
import com.eduhub.edu_hub.shared.common.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class CourseGradeController {
    private final ApiResponseFactory apiResponseFactory;
    private final CourseGradeService courseGradeService;

    @GetMapping("/course-grades")
    public ResponseEntity<ApiResponse<PageResponse<CourseGradeResponse>>> findCourseGrades(
            @AuthenticationPrincipal Jwt principal, SearchCourseGradesRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(apiResponseFactory.success(courseGradeService.findCourseGrades(principal, request)));
    }

    @PatchMapping("/enrollments/{enrollmentId}/course-grade")
    public ApiResponse<CourseGradeResponse> updateCourseGrade(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable Long enrollmentId,
            @RequestBody UpdateCourseGradeRequest request) {
        return apiResponseFactory.success(courseGradeService.updateCourseGrade(principal, enrollmentId, request));
    }

    @GetMapping("/module-classes/{moduleClassId}/course-grades/template")
    public ResponseEntity<byte[]> downloadTemplate(
            @AuthenticationPrincipal Jwt principal, @PathVariable Long moduleClassId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=module-class-" + moduleClassId + "-course-grades.xlsx")
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(courseGradeService.createTemplate(principal, moduleClassId));
    }

    @PostMapping(
            value = "/module-classes/{moduleClassId}/course-grades/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<CourseGradeResponse>> importCourseGrades(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable Long moduleClassId,
            @RequestPart("file") MultipartFile file) {
        return apiResponseFactory.success(
                courseGradeService.importCourseGrades(principal, moduleClassId, new ImportCourseGradesRequest(file)));
    }
}
