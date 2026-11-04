package com.eduhub.edu_hub.identity.application;

public record CreateStudentAccountResult(Long userId, boolean emailSent, String emailError) {}
