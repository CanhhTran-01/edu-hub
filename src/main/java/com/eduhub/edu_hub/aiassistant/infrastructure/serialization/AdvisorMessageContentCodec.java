package com.eduhub.edu_hub.aiassistant.infrastructure.serialization;

import com.eduhub.edu_hub.aiassistant.api.dto.response.AdvisorMessagePayloadResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonPOJOBuilder;

/** Chỉ nhận envelope do ứng dụng biết; text cũ hoặc JSON không phải envelope vẫn được giữ nguyên. */
@Component
public class AdvisorMessageContentCodec {
    private final ObjectMapper objectMapper;

    public AdvisorMessageContentCodec(ObjectMapper objectMapper) {
        // DTO kỳ hiện có chỉ có Lombok builder; mixin chỉ áp dụng reader payload, không đổi JSON API Scheduling.
        this.objectMapper = objectMapper
                .rebuild()
                .addMixIn(SemesterResponse.class, SemesterSnapshotMixin.class)
                .addMixIn(SemesterResponse.SemesterResponseBuilder.class, SemesterSnapshotBuilderMixin.class)
                .build();
    }

    public String encode(AdvisorMessagePayloadResponse payload) {
        return objectMapper.writeValueAsString(payload);
    }

    public DecodedMessage decode(String storedContent) {
        if (storedContent == null || !storedContent.stripLeading().startsWith("{"))
            return new DecodedMessage(storedContent, null);
        String displayText = storedContent;
        try {
            var document = objectMapper.readTree(storedContent);
            if (!document.isObject()
                    || !document.path("type").isString()
                    || !AdvisorMessagePayloadResponse.TYPE.equals(
                            document.path("type").asString())
                    || !document.path("text").isString()) return new DecodedMessage(storedContent, null);
            String text = document.path("text").asString();
            displayText = text;
            // Phiên bản chưa hỗ trợ chỉ hiển thị text, không đưa giả định chưa hiểu vào context tính toán.
            if (!document.path("schemaVersion").isInt()
                    || document.path("schemaVersion").asInt() != AdvisorMessagePayloadResponse.SCHEMA_VERSION)
                return new DecodedMessage(text, null);
            var payload = objectMapper.treeToValue(document, AdvisorMessagePayloadResponse.class);
            var goal = payload.gpaGoal();
            if (goal != null
                    && (goal.status() == null
                            || goal.snapshot() == null
                            || goal.snapshot().semester() == null
                            || goal.snapshot().semester().getId() == null
                            || goal.conditions() == null
                            || goal.pendingConditions() == null
                            || goal.calculatedAt() == null)) return new DecodedMessage(text, null);
            return new DecodedMessage(text, payload);
        } catch (RuntimeException exception) {
            return new DecodedMessage(displayText, null);
        }
    }

    public record DecodedMessage(String text, AdvisorMessagePayloadResponse payload) {}

    @JsonDeserialize(builder = SemesterResponse.SemesterResponseBuilder.class)
    private abstract static class SemesterSnapshotMixin {}

    @JsonPOJOBuilder(withPrefix = "")
    private abstract static class SemesterSnapshotBuilderMixin {}
}
