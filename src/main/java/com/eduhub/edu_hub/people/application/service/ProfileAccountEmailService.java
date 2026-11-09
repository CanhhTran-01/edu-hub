package com.eduhub.edu_hub.people.application.service;

import com.eduhub.edu_hub.identity.api.dto.response.UserAccountEmailResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Đọc email tài khoản theo lô để bổ sung vào hồ sơ sinh viên, giảng viên. */
@Service
@RequiredArgsConstructor
public class ProfileAccountEmailService {
    private final IdentityFacade identityFacade;

    public Map<Long, String> findEmailsByUserIds(Collection<Long> userIds) {
        var linkedUserIds = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (linkedUserIds.isEmpty()) return Map.of();
        // Một lần gọi facade cho cả trang; email null/không có tài khoản đều hiển thị null, không đọc User entity.
        return identityFacade.findAccountEmailsByUserIds(linkedUserIds).stream()
                .filter(account -> account.email() != null)
                .collect(Collectors.toMap(UserAccountEmailResponse::userId, UserAccountEmailResponse::email));
    }
}
