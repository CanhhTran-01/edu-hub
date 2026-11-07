package com.eduhub.edu_hub.identity.application.result;

public record StudentAccountInfo(
        Long userId, String username, String email, String status, boolean firstLogin, boolean locked) {}
