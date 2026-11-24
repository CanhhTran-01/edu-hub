package com.eduhub.edu_hub.curriculum.api.dto.response;

/** Giữ tên ngành lịch sử và trạng thái xóa để consumer áp đúng quy tắc của từng use-case. */
public record MajorSimpleResponse(Long id, String majorCode, String majorName, Long facultyId, boolean deleted) {}
