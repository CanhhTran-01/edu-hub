package com.eduhub.edu_hub.identity.api.dto.response;

public record StudentAccountResponse(
        Long userId, String username, String email, String status, boolean firstLogin, boolean locked) {}
