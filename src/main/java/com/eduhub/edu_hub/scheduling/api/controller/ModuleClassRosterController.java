package com.eduhub.edu_hub.scheduling.api.controller;

import com.eduhub.edu_hub.scheduling.api.dto.request.ExportModuleClassStudentsRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.ModuleClassRosterSearchRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassRosterResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassSimpleResponse;
import com.eduhub.edu_hub.scheduling.application.service.ModuleClassExportService;
import com.eduhub.edu_hub.scheduling.application.service.ModuleClassRosterService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ModuleClassRosterController {
    private final ApiResponseFactory apiResponseFactory;
    private final ModuleClassRosterService rosterService;
    private final ModuleClassExportService exportService;

    @GetMapping("/semesters/{semesterId}/module-classes/export-options")
    public ApiResponse<PageResponse<ModuleClassSimpleResponse>> searchClasses(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable Long semesterId,
            @Valid @ModelAttribute ModuleClassRosterSearchRequest request) {
        return apiResponseFactory.success(rosterService.searchClasses(principal, semesterId, request));
    }

    @GetMapping("/module-classes/{moduleClassId}/roster")
    public ApiResponse<ModuleClassRosterResponse> findRoster(
            @AuthenticationPrincipal Jwt principal, @PathVariable Long moduleClassId) {
        return apiResponseFactory.success(rosterService.findRoster(principal, moduleClassId));
    }

    @PostMapping("/module-classes/{moduleClassId}/students/export")
    public ResponseEntity<byte[]> exportStudents(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable Long moduleClassId,
            @Valid @RequestBody ExportModuleClassStudentsRequest request) {
        byte[] spreadsheet = exportService.exportStudents(principal, moduleClassId, request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=module-class-" + moduleClassId + "-students.xlsx")
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(spreadsheet);
    }
}
