package com.eduhub.edu_hub.notification.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.notification.domain.repository.QueuedAdmissionNotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class AdmissionNotificationDispatchServiceTest {
    private final QueuedAdmissionNotificationRepository notificationRepository =
            mock(QueuedAdmissionNotificationRepository.class);
    private final QueuedAdmissionNotificationSender notificationSender = mock(QueuedAdmissionNotificationSender.class);
    private final AdmissionNotificationDispatchService dispatchService =
            new AdmissionNotificationDispatchService(notificationRepository, notificationSender);

    @Test
    void schedulerSelectsPendingAndFailedBelowFourFailuresThenCallsSender() {
        when(notificationRepository.findDueNotificationIds(
                        anyList(), any(LocalDateTime.class), eq(4), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L));

        dispatchService.sendDueNotifications();

        verify(notificationRepository)
                .findDueNotificationIds(
                        eq(List.of(NotificationStatus.PENDING, NotificationStatus.FAILED)),
                        any(LocalDateTime.class),
                        eq(4),
                        argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 100));
        var deliveryOrder = inOrder(notificationSender);
        deliveryOrder.verify(notificationSender).sendScheduledAttempt(1L);
        deliveryOrder.verify(notificationSender).sendScheduledAttempt(2L);
        verify(notificationSender, never()).sendInitialAttempt(anyLong());
    }

    @Test
    void emptyQueueDoesNotAttemptDelivery() {
        when(notificationRepository.findDueNotificationIds(
                        anyList(), any(LocalDateTime.class), eq(4), any(Pageable.class)))
                .thenReturn(List.of());

        dispatchService.sendDueNotifications();

        verifyNoInteractions(notificationSender);
    }
}
