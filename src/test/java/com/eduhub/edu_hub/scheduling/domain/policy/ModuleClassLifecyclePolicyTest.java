package com.eduhub.edu_hub.scheduling.domain.policy;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.TimetableProblem;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModuleClassLifecyclePolicyTest {
    private final LocalDate registrationEnd = LocalDate.of(2027, 7, 30);
    private final LocalDate lastDate = LocalDate.of(2027, 8, 9);
    private final TimetableProblem.ExistingSession schedule =
            new TimetableProblem.ExistingSession(1L, 1L, 201L, 501L, 2, 1, 3, lastDate, lastDate);

    @Test
    void keepsRegistrationOpenUntilEndOfClosingDay() {
        assertThat(ModuleClassLifecyclePolicy.determineNextStatus(
                        ModuleClassStatus.OPEN, registrationEnd, registrationEnd, 3, List.of(schedule), true))
                .isEqualTo(ModuleClassStatus.OPEN);
        assertThat(ModuleClassLifecyclePolicy.determineNextStatus(
                        ModuleClassStatus.OPEN,
                        registrationEnd,
                        registrationEnd.plusDays(1),
                        3,
                        List.of(schedule),
                        true))
                .isEqualTo(ModuleClassStatus.CLOSED);
    }

    @Test
    void completesAfterLastDayOnlyWhenScheduleIsComplete() {
        assertThat(ModuleClassLifecyclePolicy.determineNextStatus(
                        ModuleClassStatus.CLOSED, registrationEnd, lastDate, 3, List.of(schedule), true))
                .isEqualTo(ModuleClassStatus.CLOSED);
        assertThat(ModuleClassLifecyclePolicy.determineNextStatus(
                        ModuleClassStatus.CLOSED, registrationEnd, lastDate.plusDays(1), 3, List.of(schedule), true))
                .isEqualTo(ModuleClassStatus.COMPLETED);
        assertThat(ModuleClassLifecyclePolicy.determineNextStatus(
                        ModuleClassStatus.CLOSED, registrationEnd, lastDate.plusDays(1), 6, List.of(schedule), true))
                .isEqualTo(ModuleClassStatus.CLOSED);
        assertThat(ModuleClassLifecyclePolicy.determineNextStatus(
                        ModuleClassStatus.CLOSED, registrationEnd, lastDate.plusDays(1), 3, List.of(), true))
                .isEqualTo(ModuleClassStatus.CLOSED);
    }

    @Test
    void doesNotCompleteCancelledOrUnscheduledClasses() {
        for (var status :
                List.of(ModuleClassStatus.CANCELLED, ModuleClassStatus.UNSCHEDULED, ModuleClassStatus.SCHEDULED))
            assertThat(ModuleClassLifecyclePolicy.determineNextStatus(
                            status, registrationEnd, lastDate.plusDays(1), 3, List.of(schedule), true))
                    .isEqualTo(status);
    }
}
