package com.eduhub.edu_hub.enrollment.api.serialization;

import java.math.BigDecimal;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Không ép chuỗi/boolean thành điểm; giữ nguyên số thập phân để kiểm tra trước khi lưu. */
public class ProcessScoreDeserializer extends ValueDeserializer<BigDecimal> {
    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT) && !parser.hasToken(JsonToken.VALUE_NUMBER_FLOAT))
            return context.reportInputMismatch(BigDecimal.class, "Điểm phải là số JSON hoặc null");
        return parser.getDecimalValue();
    }
}
