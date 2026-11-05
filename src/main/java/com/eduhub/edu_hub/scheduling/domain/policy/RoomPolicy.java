package com.eduhub.edu_hub.scheduling.domain.policy;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RoomPolicy {
    public static final String CODE_PATTERN = "[0-9]+-[A-Za-z0-9]+";
    public static final String CODE_FORMAT_MESSAGE = "Mã phòng phải theo định dạng <số phòng>-<tòa nhà>, ví dụ 102-TA1";
}
