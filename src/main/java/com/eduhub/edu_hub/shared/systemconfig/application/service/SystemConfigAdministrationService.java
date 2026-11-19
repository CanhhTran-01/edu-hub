package com.eduhub.edu_hub.shared.systemconfig.application.service;

import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.domain.enums.AccessTokenPurpose;
import com.eduhub.edu_hub.identity.domain.enums.RoleName;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.systemconfig.api.dto.request.UpdateSystemConfigsRequest;
import com.eduhub.edu_hub.shared.systemconfig.api.dto.response.SystemConfigResponse;
import com.eduhub.edu_hub.shared.systemconfig.application.exception.SystemConfigException;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** Kiểm tra người quản trị riêng trước thao tác; tách khỏi nguồn đọc để tránh vòng phụ thuộc Identity. */
@Service
@RequiredArgsConstructor
public class SystemConfigAdministrationService {
    private final IdentityFacade identityFacade;
    private final SystemConfigService systemConfigService;

    public List<SystemConfigResponse> findConfigurations(Jwt principal, SystemConfigKey.Group group) {
        requireSystemAdministrator(principal);
        return systemConfigService.findConfigurations(group);
    }

    public List<SystemConfigResponse> updateConfigurations(Jwt principal, UpdateSystemConfigsRequest request) {
        Long actorUserId = requireSystemAdministrator(principal);
        return systemConfigService.updateConfigurations(request, actorUserId);
    }

    private Long requireSystemAdministrator(Jwt principal) {
        if (principal == null) throw new SystemConfigException(ErrorCode.UNAUTHENTICATED);
        if (!AccessTokenPurpose.ACCESS.name().equals(principal.getClaim(AccessTokenPurpose.CLAIM_NAME)))
            throw new SystemConfigException(ErrorCode.FIRST_LOGIN_REQUIRED);
        var actor = identityFacade.requireCurrentUser(principal);
        if (!UserStatus.ACTIVE.name().equals(actor.getStatus())
                || !RoleName.SYSTEM_ADMIN.name().equals(actor.getRole()))
            throw new SystemConfigException(ErrorCode.UNAUTHORIZED);
        return actor.getId();
    }
}
