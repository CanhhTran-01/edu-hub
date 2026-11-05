package com.eduhub.edu_hub.identity.application.result;

public record CreateStudentAccountResult(Long userId, boolean emailSent, String emailError) {}
