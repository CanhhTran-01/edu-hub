package com.eduhub.edu_hub.admission.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.repository.*;
import com.eduhub.edu_hub.notification.api.dto.request.SearchAdmissionNotificationsRequest;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.*;
import org.junit.jupiter.api.Test;

class AdmissionNotificationQueryServiceTest {
    private final AdmissionPlanRepository plans = mock(AdmissionPlanRepository.class);
    private final StudentAdmissionRepository admissions = mock(StudentAdmissionRepository.class);
    private final NotificationFacade.AdmissionQueue notifications = mock(NotificationFacade.AdmissionQueue.class);
    private final AdmissionNotificationQueryService service =
            new AdmissionNotificationQueryService(plans, admissions, notifications);

    @Test
    void checksThePlanBeforeExposingProgressOrNotificationDetails() {
        assertThatThrownBy(() -> service.getProgress(7L))
                .isInstanceOfSatisfying(AdmissionPlanException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ADMISSION_PLAN_NOT_FOUND));
        verifyNoInteractions(admissions, notifications);
    }

    @Test
    void passesOnlyTheSavedAdmissionIdsAndCurrentConfirmationState() {
        when(plans.findByIdAndDeletedFalse(7L))
                .thenReturn(Optional.of(AdmissionPlan.builder()
                        .state(AdmissionPlanStatus.CONFIRMING)
                        .build()));
        when(admissions.findIdsByPlanId(7L)).thenReturn(List.of(11L, 12L));
        service.getProgress(7L);
        verify(notifications).getNotificationProgress(7L, List.of(11L, 12L), true, false);
    }

    @Test
    void passesCompletionWhenConfirmationHasFinishedEvenWithNoSavedNotifications() {
        when(plans.findByIdAndDeletedFalse(7L))
                .thenReturn(Optional.of(AdmissionPlan.builder()
                        .state(AdmissionPlanStatus.PARTIALLY_CONFIRMED)
                        .build()));
        service.getProgress(7L);
        verify(notifications).getNotificationProgress(7L, List.of(), false, true);
    }

    @Test
    void validatesPagingBeforeCallingNotificationFacade() {
        when(plans.findByIdAndDeletedFalse(7L))
                .thenReturn(Optional.of(AdmissionPlan.builder().build()));
        var request = new SearchAdmissionNotificationsRequest();
        request.setSize(101);
        assertThatThrownBy(() -> service.searchNotifications(7L, request)).isInstanceOf(AdmissionPlanException.class);
        verifyNoInteractions(notifications);
    }
}
