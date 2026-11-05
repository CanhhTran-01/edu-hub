package com.eduhub.edu_hub.scheduling.domain.policy;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ModuleClassPolicy {
    // TODO(CAPACITY_CONFIG): đồng bộ cả constraint database khi dùng cấu hình động.
    public static final int MAX_CAPACITY = 80;
}
