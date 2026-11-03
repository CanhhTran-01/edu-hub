package com.eduhub.edu_hub.scheduling.infrastructure;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.scheduling.application.port.ModuleClassEnrollmentQuery;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ByteArrayResource;

class DevModuleClassEnrollmentQueryTest {
    @ParameterizedTest
    @ValueSource(strings = {"MC_9_CS101_002", "MC_9_CS101_003"})
    void droppedAndHiddenEnrollmentsBothCountAsHistoricalRegistrations(String code) {
        var result = new DevModuleClassEnrollmentQuery().summarize(1L, code);
        assertThat(result.historyComplete()).isTrue();
        assertThat(result.enrollmentCount()).isEqualTo(1);
        assertThat(result.studentCount()).isEqualTo(1);
    }

    @Test
    void unlistedClassesHaveNoRegistrationsInTheExplicitlyCompleteDevDataset() {
        var result = new DevModuleClassEnrollmentQuery().summarize(123L, "MC_9_CS101_001");
        assertThat(result.historyComplete()).isTrue();
        assertThat(result.enrollmentCount()).isZero();
        assertThat(result.dataSource()).isEqualTo("DEV_FIXTURE");
    }

    @Test
    void incompleteFixtureKeepsItsUnknownHistoryFlag() {
        var resource =
                new ByteArrayResource("{\"historyComplete\":false,\"classes\":[]}".getBytes(StandardCharsets.UTF_8));
        var result = new DevModuleClassEnrollmentQuery(resource).summarize(1L, "ANY_CLASS");
        assertThat(result.historyComplete()).isFalse();
    }

    @Test
    void productionProfileDoesNotInstallTheFixtureAndFailsClosedUntilRealIntegrationExists() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("prod");
            context.register(DevModuleClassEnrollmentQuery.class, UnavailableModuleClassEnrollmentQuery.class);
            context.refresh();
            var query = context.getBean(ModuleClassEnrollmentQuery.class);
            assertThat(query).isInstanceOf(UnavailableModuleClassEnrollmentQuery.class);
            assertThatThrownBy(() -> query.summarize(1L, "MC_9_CS101_001"))
                    .isInstanceOfSatisfying(BaseException.class, error -> assertThat(error.getErrorCode())
                            .isEqualTo(ErrorCode.MODULE_CLASS_ENROLLMENT_DATA_UNAVAILABLE));
        }
    }
}
