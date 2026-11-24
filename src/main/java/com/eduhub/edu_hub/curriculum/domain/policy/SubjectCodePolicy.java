package com.eduhub.edu_hub.curriculum.domain.policy;

import com.eduhub.edu_hub.curriculum.application.exception.SubjectException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.Locale;

/** STT chung toàn hệ thống, tối thiểu sáu chữ số; mã học phần độc lập với ngành và khóa. */
public final class SubjectCodePolicy {
    private SubjectCodePolicy() {}

    public static String generateCode(Long subjectSequence) {
        if (subjectSequence == null || subjectSequence < 1) throw new SubjectException(ErrorCode.UNEXPECTED_ERROR);
        return String.format(Locale.ROOT, "HP%06d", subjectSequence);
    }
}
