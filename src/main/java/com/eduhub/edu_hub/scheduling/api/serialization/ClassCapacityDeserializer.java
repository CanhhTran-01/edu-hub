package com.eduhub.edu_hub.scheduling.api.serialization;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Tránh Jackson tự cắt 10.5 thành 10 trước khi Bean Validation kiểm tra sức chứa. */
public class ClassCapacityDeserializer extends ValueDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return context.reportInputMismatch(Integer.class, "Sức chứa phải là số nguyên");
        }
        return parser.getIntValue();
    }
}
