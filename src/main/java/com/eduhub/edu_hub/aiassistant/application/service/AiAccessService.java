package com.eduhub.edu_hub.aiassistant.application.service;

import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.domain.enums.RoleName;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiAccessService {
    private final IdentityFacade identityFacade;

    public UserInfoResponse requireStudent(Jwt jwt) {
        return requireRole(jwt, RoleName.STUDENT);
    }

    public UserInfoResponse requireAcademicAdmin(Jwt jwt) {
        return requireRole(jwt, RoleName.ACADEMIC_ADMIN);
    }

    private UserInfoResponse requireRole(Jwt jwt, RoleName requiredRole) {
        UserInfoResponse user = identityFacade.requireCurrentUser(jwt);
        if (!UserStatus.ACTIVE.name().equals(user.getStatus())
                || !requiredRole.name().equals(user.getRole())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }
}
