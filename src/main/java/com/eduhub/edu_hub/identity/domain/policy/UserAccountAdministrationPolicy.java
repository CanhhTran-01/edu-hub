package com.eduhub.edu_hub.identity.domain.policy;

import com.eduhub.edu_hub.identity.application.exception.IdentityAccountException;
import com.eduhub.edu_hub.identity.domain.enums.RoleName;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

/** Quy tắc khóa/mở khóa/reset; service cung cấp trạng thái và số admin sau khi đã giữ khóa cần thiết. */
public final class UserAccountAdministrationPolicy {
    private static final int MAXIMUM_LOCK_REASON_LENGTH = 500;

    private UserAccountAdministrationPolicy() {}

    public static void validateTargetAccountStatus(UserStatus targetStatus) {
        // Vô hiệu hóa không phải khóa đăng nhập
        // không dùng unlock để kích hoạt lại tài khoản DISABLED.
        if (targetStatus == UserStatus.DISABLED) {
            throw new IdentityAccountException(ErrorCode.DISABLED_ACCOUNT_OPERATION_NOT_ALLOWED);
        }
    }

    public static void validateSelfLock(
            Long actorUserId, String actorRoleName, Long targetUserId, long activeSystemAdminCount) {
        if (actorUserId.equals(targetUserId)
                && RoleName.SYSTEM_ADMIN.name().equals(actorRoleName)
                && activeSystemAdminCount <= 1) {
            throw new IdentityAccountException(ErrorCode.UNAUTHORIZED);
        }
    }

    public static void validateLockReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > MAXIMUM_LOCK_REASON_LENGTH) {
            throw new IdentityAccountException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
