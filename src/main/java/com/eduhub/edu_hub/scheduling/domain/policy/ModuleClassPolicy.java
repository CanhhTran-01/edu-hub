package com.eduhub.edu_hub.scheduling.domain.policy;

import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ModuleClassPolicy {
    public static String normalizeChangeReason(String reason) {
        if (reason == null || reason.isBlank()) throw new ModuleClassException(ErrorCode.VALIDATION_ERROR);
        return reason.strip();
    }

    public static String generateModuleClassCode(Long semesterId, String subjectCode, int classSequence) {
        // Mã lớp dạng MC_<semesterId>_<mã môn>_<seq 3 chữ số>
        return String.format(Locale.ROOT, "MC_%d_%s_%03d", semesterId, subjectCode, classSequence);
    }
}
