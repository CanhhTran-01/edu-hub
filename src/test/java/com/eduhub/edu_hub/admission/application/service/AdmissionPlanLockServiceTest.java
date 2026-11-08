package com.eduhub.edu_hub.admission.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdmissionPlanLockServiceTest {
    private final AdmissionPlanRepository admissionPlanRepository = mock(AdmissionPlanRepository.class);
    private final AdmissionPlanLockService planLockService = new AdmissionPlanLockService(admissionPlanRepository);

    @Test
    void returnsTheLockedInstanceWithoutChangingItsRevision() {
        AdmissionPlan admissionPlan = AdmissionPlan.builder().revision(3).build();
        when(admissionPlanRepository.findForUpdate(1L)).thenReturn(Optional.of(admissionPlan));

        assertThat(planLockService.lockPlanAtRevision(1L, 3)).isSameAs(admissionPlan);
        assertThat(admissionPlan.getRevision()).isEqualTo(3);
    }

    @Test
    void rejectsAStaleRevisionWithTheExistingErrorCode() {
        when(admissionPlanRepository.findForUpdate(1L))
                .thenReturn(Optional.of(AdmissionPlan.builder().revision(3).build()));

        assertThatThrownBy(() -> planLockService.lockPlanAtRevision(1L, 2))
                .isInstanceOfSatisfying(AdmissionPlanException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ADMISSION_STALE_PLAN));
    }

    @Test
    void rejectsAMissingPlanWithTheExistingErrorCode() {
        when(admissionPlanRepository.findForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> planLockService.lockPlanAtRevision(1L, 0))
                .isInstanceOfSatisfying(AdmissionPlanException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ADMISSION_PLAN_NOT_FOUND));
    }
}
