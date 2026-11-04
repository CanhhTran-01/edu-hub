package com.eduhub.edu_hub.scheduling.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.enrollment.api.dto.ModuleClassUsageResponse;
import com.eduhub.edu_hub.enrollment.application.EnrollmentFacade;
import com.eduhub.edu_hub.scheduling.application.port.ModuleClassEnrollmentQuery;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class FacadeModuleClassEnrollmentQueryTest {
    @ParameterizedTest
    @ValueSource(strings = {"dev", "test", "prod"})
    void everyProfileRequiresRealEnrollmentData(String profile) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profile);
            context.register(FacadeModuleClassEnrollmentQuery.class);
            context.refresh();
            var query = context.getBean(ModuleClassEnrollmentQuery.class);
            assertThatThrownBy(() -> query.summarize(1L, "ANY_CLASS"))
                    .isInstanceOfSatisfying(BaseException.class, error -> assertThat(error.getErrorCode())
                            .isEqualTo(ErrorCode.MODULE_CLASS_ENROLLMENT_DATA_UNAVAILABLE));
        }
    }

    @Test
    void queriesTheRealClassIdAndPreservesCompleteHistoricalCounts() {
        var enrollment = mock(EnrollmentFacade.Planning.class);
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("enrollment", enrollment);
        var query = new FacadeModuleClassEnrollmentQuery(beans.getBeanProvider(EnrollmentFacade.Planning.class));
        when(enrollment.summarizeModuleClass(17L)).thenReturn(new ModuleClassUsageResponse(true, 3, 2, "DATABASE"));

        var result = query.summarize(17L, "CODE_DOES_NOT_IDENTIFY_THE_QUERY");

        assertThat(result.enrollmentCount()).isEqualTo(3);
        assertThat(result.studentCount()).isEqualTo(2);
        assertThat(result.historyComplete()).isTrue();
        verify(enrollment).summarizeModuleClass(17L);
    }
}
