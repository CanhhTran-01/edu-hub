package com.eduhub.edu_hub.aiassistant.infrastructure.serialization;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.aiassistant.api.dto.response.AdvisorMessagePayloadResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AdvisorMessageContentCodecTest {
    AdvisorMessageContentCodec codec = new AdvisorMessageContentCodec(
            JsonMapper.builder().findAndAddModules().build());

    @Test
    void legacyTextAndOrdinaryJsonAreNotMisidentifiedAsTrustedPayloads() {
        for (String text : new String[] {"Tin cũ [K21]", "{nội dung chưa phải JSON}", "{\"text\":\"Ví dụ JSON\"}"}) {
            assertThat(codec.decode(text).text()).isEqualTo(text);
            assertThat(codec.decode(text).payload()).isNull();
        }
    }

    @Test
    void unknownVersionDisplaysItsTextWithoutReusingUnsupportedState() {
        var decoded =
                codec.decode("{\"schemaVersion\":99,\"type\":\"eduhub.advisor.message\",\"text\":\"Kết quả cũ\"}");
        assertThat(decoded.text()).isEqualTo("Kết quả cũ");
        assertThat(decoded.payload()).isNull();
    }

    @Test
    void supportedClarificationPreservesItsProvenance() {
        var payload = new AdvisorMessagePayloadResponse(
                1, AdvisorMessagePayloadResponse.TYPE, "Bạn muốn giữ điều kiện nào?", true, null, null);
        assertThat(codec.decode(codec.encode(payload)).payload()).isEqualTo(payload);
    }
}
