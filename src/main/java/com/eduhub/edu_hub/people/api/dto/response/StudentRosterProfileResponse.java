package com.eduhub.edu_hub.people.api.dto.response;

/** Danh tính hiển thị trên danh sách lớp, không tải thông tin tài khoản hoặc liên hệ. */
public record StudentRosterProfileResponse(Long studentId, String studentCode, String fullName) {}
