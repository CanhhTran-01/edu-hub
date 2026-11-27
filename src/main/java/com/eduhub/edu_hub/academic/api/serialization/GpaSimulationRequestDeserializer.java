package com.eduhub.edu_hub.academic.api.serialization;

import com.eduhub.edu_hub.academic.api.dto.request.GpaSimulationRequest;
import com.eduhub.edu_hub.academic.api.dto.request.GpaSimulationRequest.CourseAssumptionRequest;
import com.eduhub.edu_hub.academic.application.exception.GpaSimulationException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/** Không ép chuỗi, boolean hoặc ID thập phân thành đầu vào mô phỏng hợp lệ. */
public class GpaSimulationRequestDeserializer extends ValueDeserializer<GpaSimulationRequest> {
    @Override
    public GpaSimulationRequest deserialize(JsonParser parser, DeserializationContext context) {
        JsonNode request = parser.readValueAsTree();
        requireFields(request, Set.of("snapshotRef", "assumptions"), "request");
        JsonNode snapshotRef = request.get("snapshotRef");
        if (snapshotRef == null || !snapshotRef.isString()) throw invalid("snapshotRef");
        JsonNode assumptions = request.get("assumptions");
        if (assumptions == null
                || !assumptions.isArray()
                || assumptions.size() > GpaSimulationRequest.MAXIMUM_ASSUMPTIONS) throw invalid("assumptions");
        var courseAssumptions = new ArrayList<CourseAssumptionRequest>();
        for (int index = 0; index < assumptions.size(); index++) {
            JsonNode assumption = assumptions.get(index);
            String field = "assumptions[" + index + "]";
            requireFields(assumption, Set.of("enrollmentId", "grade4"), field);
            courseAssumptions.add(new CourseAssumptionRequest(
                    readId(assumption.get("enrollmentId"), field + ".enrollmentId"),
                    readGrade(assumption.get("grade4"), field + ".grade4")));
        }
        return new GpaSimulationRequest(snapshotRef.asString(), List.copyOf(courseAssumptions));
    }

    private void requireFields(JsonNode object, Set<String> allowedFields, String field) {
        if (object == null || !object.isObject()) throw invalid(field);
        for (String property : object.propertyNames()) {
            if (!allowedFields.contains(property))
                throw invalid(field.equals("request") ? property : field + "." + property);
        }
    }

    private Long readId(JsonNode identifier, String field) {
        if (identifier == null
                || !identifier.isIntegralNumber()
                || !identifier.canConvertToLong()
                || identifier.longValue() <= 0) throw invalid(field);
        return identifier.longValue();
    }

    private BigDecimal readGrade(JsonNode grade, String field) {
        if (grade == null || !grade.isNumber()) throw invalid(field);
        BigDecimal grade4 = grade.decimalValue();
        if (grade4.signum() < 0
                || grade4.compareTo(BigDecimal.valueOf(4)) > 0
                || grade4.stripTrailingZeros().scale() > 2) throw invalid(field);
        return grade4.stripTrailingZeros();
    }

    private GpaSimulationException invalid(String field) {
        return new GpaSimulationException(ErrorCode.VALIDATION_ERROR, field, "Dữ liệu mô phỏng không hợp lệ.");
    }
}
