package com.eduhub.edu_hub.scheduling.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.enrollment.api.dto.response.ModuleClassUsageResponse;
import com.eduhub.edu_hub.enrollment.application.facade.EnrollmentFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class ModuleClassEnrollmentQueryServiceTest {
    @Test
    void queriesTheRealClassIdAndPreservesCompleteHistoricalCounts() {
        var enrollment = mock(EnrollmentFacade.Planning.class);
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("enrollment", enrollment);
        var moduleClassEnrollmentQueryService =
                new ModuleClassEnrollmentQueryService(beans.getBeanProvider(EnrollmentFacade.Planning.class));
        when(enrollment.summarizeModuleClass(17L)).thenReturn(new ModuleClassUsageResponse(true, 3, 2, "DATABASE"));

        var result = moduleClassEnrollmentQueryService.summarizeModuleClassEnrollments(17L);

        assertThat(result.enrollmentCount()).isEqualTo(3);
        assertThat(result.studentCount()).isEqualTo(2);
        assertThat(result.historyComplete()).isTrue();
        verify(enrollment).summarizeModuleClass(17L);
    }
}
