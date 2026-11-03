package com.eduhub.edu_hub.scheduling.infrastructure;

import com.eduhub.edu_hub.scheduling.application.port.*;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile({"dev", "test"})
public class DevAcademicPlanningDataSource implements AcademicPlanningDataSource {
    private final PlanningSnapshot data;

    public DevAcademicPlanningDataSource() {
        try (var input = new ClassPathResource("fixtures/module-class-planning.json").getInputStream()) {
            data = JsonMapper.builder().build().readValue(input, PlanningSnapshot.class);
            data.validate();
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Không đọc được fixture lập kế hoạch học phần", exception);
        }
    }

    @Override
    public PlanningSnapshot snapshot() {
        return data;
    }
}
