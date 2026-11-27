package com.eduhub.edu_hub.aiassistant.api.dto.response;

import com.eduhub.edu_hub.academic.api.dto.response.AdvisorAcademicProfileResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaGoalResponse;
import com.eduhub.edu_hub.academic.api.dto.response.PrerequisiteImpactResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.MinimumExamScoreResponse;

/** Cùng một payload dùng khi lưu content TEXT, hoàn tất SSE và mở lại lịch sử. */
public record AdvisorMessagePayloadResponse(
        int schemaVersion,
        String type,
        String text,
        boolean clarification,
        AdvisorAcademicProfileResponse academicProfile,
        GpaGoalResponse gpaGoal,
        PrerequisiteImpactResponse prerequisiteImpact,
        MinimumExamScoreResponse examScore) {

    public static final int SCHEMA_VERSION = 2;
    public static final String TYPE = "eduhub.advisor.message";

    public AdvisorMessagePayloadResponse(
            int schemaVersion,
            String type,
            String text,
            boolean clarification,
            AdvisorAcademicProfileResponse academicProfile,
            GpaGoalResponse gpaGoal) {
        this(schemaVersion, type, text, clarification, academicProfile, gpaGoal, null, null);
    }

    public AdvisorMessagePayloadResponse withText(String text) {
        return new AdvisorMessagePayloadResponse(
                schemaVersion, type, text, clarification, academicProfile, gpaGoal, prerequisiteImpact, examScore);
    }
}
