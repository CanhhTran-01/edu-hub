package com.eduhub.edu_hub.enrollment.api.dto.response;

/** Giữ kỳ của kết quả đạt, kể cả lượt đã ẩn, để Academic xét đúng mốc trước kỳ/đến hiện tại. */
public record PassedSubjectSemesterResponse(Long subjectId, Long semesterId) {}
