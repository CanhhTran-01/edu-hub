package com.eduhub.edu_hub.notification.infrastructure.dispatch;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.notification.application.event.AdmissionNotificationQueued;
import com.eduhub.edu_hub.notification.application.service.QueuedAdmissionNotificationSender;
import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.notification.domain.repository.QueuedAdmissionNotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class AdmissionNotificationDispatcherTest {
    private final QueuedAdmissionNotificationRepository notifications =
            mock(QueuedAdmissionNotificationRepository.class);
    private final QueuedAdmissionNotificationSender sender = mock(QueuedAdmissionNotificationSender.class);
    private final AdmissionNotificationDispatcher dispatcher =
            new AdmissionNotificationDispatcher(notifications, sender);

    @Test
    void asyncKickoffOnlyUsesInitialDeliveryPath() {
        dispatcher.onAdmissionNotificationQueued(new AdmissionNotificationQueued(1L));
        verify(sender).sendInitialAttempt(1L);
        verify(sender, never()).sendScheduledAttempt(anyLong());
    }

    @Test
    void schedulerSelectsPendingAndFailedBelowFourFailuresThenCallsSender() {
        when(notifications.findDueNotificationIds(anyList(), any(LocalDateTime.class), eq(4), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L));
        dispatcher.sendDueNotifications();
        verify(notifications)
                .findDueNotificationIds(
                        eq(List.of(NotificationStatus.PENDING, NotificationStatus.FAILED)),
                        any(LocalDateTime.class),
                        eq(4),
                        any(Pageable.class));
        verify(sender).sendScheduledAttempt(1L);
        verify(sender).sendScheduledAttempt(2L);
        verify(sender, never()).sendInitialAttempt(anyLong());
    }

    @Test
    void disabledDispatchSkipsBothAsyncKickoffAndScheduledPolling() {
        ReflectionTestUtils.setField(dispatcher, "admissionNotificationsEnabled", false);

        dispatcher.onAdmissionNotificationQueued(new AdmissionNotificationQueued(1L));
        dispatcher.sendDueNotifications();

        verifyNoInteractions(notifications, sender);
    }
}
