package com.eduhub.edu_hub.people.api.dto.response;

/** Danh mục khoa phục vụ lựa chọn; giữ khoa ngừng hoạt động để xem dữ liệu lịch sử. */
public record FacultyResponse(Long id, String facultyCode, String facultyName, boolean active) {}
