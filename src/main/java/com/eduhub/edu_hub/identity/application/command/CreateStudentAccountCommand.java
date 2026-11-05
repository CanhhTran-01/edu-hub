package com.eduhub.edu_hub.identity.application.command;

public record CreateStudentAccountCommand(
        String username, String email, String initialPassword, boolean requirePasswordChange) {}
