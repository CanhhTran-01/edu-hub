package com.eduhub.edu_hub.scheduling.domain.policy;

import com.eduhub.edu_hub.identity.domain.enums.RoleName;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassRosterException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

/** Giới hạn truy cập cho riêng luồng xem/xuất danh sách sinh viên lớp học phần. */
public final class ModuleClassRosterAccessPolicy {
    private ModuleClassRosterAccessPolicy() {}

    public static boolean requiresLecturerAssignment(String role) {
        if (RoleName.ACADEMIC_ADMIN.name().equals(role)) return false;
        if (RoleName.LECTURER.name().equals(role)) return true;
        throw new ModuleClassRosterException(ErrorCode.UNAUTHORIZED);
    }

    public static void requireClassAccess(String role, Long actorLecturerId, Long assignedLecturerId) {
        if (requiresLecturerAssignment(role)
                && (actorLecturerId == null || !actorLecturerId.equals(assignedLecturerId)))
            throw new ModuleClassRosterException(ErrorCode.UNAUTHORIZED);
    }
}
