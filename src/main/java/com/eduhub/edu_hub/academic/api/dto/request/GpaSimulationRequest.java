package com.eduhub.edu_hub.academic.api.dto.request;

import com.eduhub.edu_hub.academic.api.serialization.GpaSimulationRequestDeserializer;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Chỉ gửi giả định cho lượt chưa có kết quả; dữ liệu thật và danh tính luôn được BE tải lại. */
@Builder
@JsonDeserialize(using = GpaSimulationRequestDeserializer.class)
public record GpaSimulationRequest(String snapshotRef, List<CourseAssumptionRequest> assumptions) {
    public static final int MAXIMUM_ASSUMPTIONS = 1000;

    public record CourseAssumptionRequest(Long enrollmentId, BigDecimal grade4) {}
}
