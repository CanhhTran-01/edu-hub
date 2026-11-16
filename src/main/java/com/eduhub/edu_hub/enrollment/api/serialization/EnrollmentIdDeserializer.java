package com.eduhub.edu_hub.enrollment.api.serialization;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Tránh cắt ID thập phân hoặc ép chuỗi sang ID rồi cập nhật nhầm lượt học. */
public class EnrollmentIdDeserializer extends ValueDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
            return context.reportInputMismatch(Long.class, "ID lượt học phải là số nguyên JSON");
        return parser.getLongValue();
    }
}
