package com.eduhub.edu_hub.identity.domain.enums;

/** Phạm vi sử dụng JWT; không thay thế ma trận quyền theo role. */
public enum AccessTokenPurpose {
    ACCESS,
    PASSWORD_CHANGE;

    public static final String CLAIM_NAME = "token_use";
}
