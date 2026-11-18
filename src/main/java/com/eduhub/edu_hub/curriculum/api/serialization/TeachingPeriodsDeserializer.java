package com.eduhub.edu_hub.curriculum.api.serialization;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Không cắt phần thập phân hoặc ép chuỗi thành số tiết trước Bean Validation. */
public class TeachingPeriodsDeserializer extends ValueDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
            return context.reportInputMismatch(Integer.class, "Tổng số tiết phải là số nguyên");
        return parser.getIntValue();
    }
}
