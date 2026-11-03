package com.eduhub.edu_hub.scheduling.infrastructure;

import com.eduhub.edu_hub.scheduling.application.port.ModuleClassEnrollmentQuery;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile({"dev", "test"})
public class DevModuleClassEnrollmentQuery implements ModuleClassEnrollmentQuery {
    private final boolean historyComplete;
    private final Map<String, List<FixtureEnrollment>> byClassCode;

    public DevModuleClassEnrollmentQuery() {
        this(new ClassPathResource("fixtures/module-class-enrollments.json"));
    }

    DevModuleClassEnrollmentQuery(Resource resource) {
        try (var input = resource.getInputStream()) {
            var fixture = JsonMapper.builder().build().readValue(input, Fixture.class);
            if (fixture.historyComplete() == null || fixture.classes() == null)
                throw new IllegalArgumentException("Missing fixture completeness or class data");
            var classes = new HashMap<String, List<FixtureEnrollment>>();
            for (var item : fixture.classes()) {
                if (item.moduleClassCode() == null
                        || item.moduleClassCode().isBlank()
                        || item.enrollments() == null
                        || classes.put(item.moduleClassCode(), List.copyOf(item.enrollments())) != null)
                    throw new IllegalArgumentException("Invalid or duplicate fixture class code");
                for (var enrollment : item.enrollments()) {
                    if (enrollment.studentId() <= 0
                            || enrollment.status() == null
                            || !Set.of("REGISTERED", "DROPPED", "COMPLETED", "FAILED")
                                    .contains(enrollment.status()))
                        throw new IllegalArgumentException("Invalid fixture enrollment");
                }
            }
            historyComplete = fixture.historyComplete();
            byClassCode = Map.copyOf(classes);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Không đọc được fixture lịch sử đăng ký theo lớp", exception);
        }
    }

    @Override
    public Usage summarize(Long moduleClassId, String moduleClassCode) {
        // JSON là TOÀN BỘ dataset mô phỏng ở dev/test: mã không liệt kê có 0 đăng ký trong fixture.
        // Chỉ được kết luận chưa từng đăng ký khi historyComplete=true. Không áp dụng cách này cho nguồn thật.
        var enrollments = byClassCode.getOrDefault(moduleClassCode, List.of());
        return new Usage(
                historyComplete,
                enrollments.size(),
                enrollments.stream()
                        .map(FixtureEnrollment::studentId)
                        .distinct()
                        .count(),
                "DEV_FIXTURE");
    }

    private record Fixture(Boolean historyComplete, List<FixtureClass> classes) {}

    private record FixtureClass(String moduleClassCode, List<FixtureEnrollment> enrollments) {}

    private record FixtureEnrollment(long studentId, String status, boolean deleted) {}
}
