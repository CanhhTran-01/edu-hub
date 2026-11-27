package com.eduhub.edu_hub.aiassistant.api.dto.response;

import com.eduhub.edu_hub.academic.api.dto.response.AdvisorAcademicProfileResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaGoalResponse;

/** Cùng một payload dùng khi lưu content TEXT, hoàn tất SSE và mở lại lịch sử. */
public record AdvisorMessagePayloadResponse(
        int schemaVersion,
        String type,
        String text,
        boolean clarification,
        AdvisorAcademicProfileResponse academicProfile,
        GpaGoalResponse gpaGoal) {

    public static final int SCHEMA_VERSION = 1;
    public static final String TYPE = "eduhub.advisor.message";

    public AdvisorMessagePayloadResponse withText(String text) {
        return new AdvisorMessagePayloadResponse(schemaVersion, type, text, clarification, academicProfile, gpaGoal);
    }
}
