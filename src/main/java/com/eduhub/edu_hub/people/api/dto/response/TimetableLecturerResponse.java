package com.eduhub.edu_hub.people.api.dto.response;

import java.util.Set;

/** Năng lực cần cho bộ xếp lịch; không đưa tài khoản hoặc thông tin cá nhân không cần thiết qua facade. */
public record TimetableLecturerResponse(Long lecturerId, String fullName, Set<Long> teachingSubjectIds) {}
