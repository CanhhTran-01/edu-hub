package com.eduhub.edu_hub.identity.application;

public record CreateLecturerAccountCommand(String username, String email, String initialPassword) {}
