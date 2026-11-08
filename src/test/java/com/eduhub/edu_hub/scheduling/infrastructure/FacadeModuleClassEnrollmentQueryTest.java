package com.eduhub.edu_hub.scheduling.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.enrollment.application.EnrollmentFacade;
import com.eduhub.edu_hub.enrollment.application.facade.dto.ModuleClassUsageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class FacadeModuleClassEnrollmentQueryTest {
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
