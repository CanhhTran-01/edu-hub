package com.eduhub.edu_hub.admission.domain.policy;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdmissionDuplicateNamePolicy {
    public static final int MAX_SAME_NAME_PER_CLASS = 3;
}
