package com.eduhub.edu_hub.scheduling.infrastructure;

import com.eduhub.edu_hub.scheduling.application.port.PlanningSnapshot;
import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

public final class PlanningTestData {
    private PlanningTestData() {}

    public static PlanningSnapshot load() {
        try (var input = new ClassPathResource("fixtures/module-class-planning.json").getInputStream()) {
            var data = JsonMapper.builder().build().readValue(input, PlanningSnapshot.class);
            data.validate();
            return data;
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Không đọc được fixture lập kế hoạch học phần", exception);
        }
    }
}
