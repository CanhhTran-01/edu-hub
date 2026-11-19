package com.eduhub.edu_hub.shared.systemconfig.api.controller;

import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.systemconfig.api.dto.request.UpdateSystemConfigsRequest;
import com.eduhub.edu_hub.shared.systemconfig.api.dto.response.SystemConfigResponse;
import com.eduhub.edu_hub.shared.systemconfig.application.service.SystemConfigAdministrationService;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/system-configs")
@RequiredArgsConstructor
public class SystemConfigController {
    private final SystemConfigAdministrationService administrationService;
    private final ApiResponseFactory apiResponseFactory;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SystemConfigResponse>>> findConfigurations(
            @AuthenticationPrincipal Jwt principal, @RequestParam(required = false) SystemConfigKey.Group group) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(apiResponseFactory.success(administrationService.findConfigurations(principal, group)));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<List<SystemConfigResponse>>> updateConfigurations(
            @AuthenticationPrincipal Jwt principal, @Valid @RequestBody UpdateSystemConfigsRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(apiResponseFactory.success(administrationService.updateConfigurations(principal, request)));
    }
}
