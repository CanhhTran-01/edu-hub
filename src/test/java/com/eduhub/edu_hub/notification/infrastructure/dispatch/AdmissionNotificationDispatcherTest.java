package com.eduhub.edu_hub.notification.infrastructure.dispatch;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.notification.application.event.AdmissionNotificationQueued;
import com.eduhub.edu_hub.notification.application.service.AdmissionNotificationDispatchService;
import com.eduhub.edu_hub.notification.application.service.QueuedAdmissionNotificationSender;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AdmissionNotificationDispatcherTest {
    private final AdmissionNotificationDispatchService dispatchService =
            mock(AdmissionNotificationDispatchService.class);
    private final QueuedAdmissionNotificationSender sender = mock(QueuedAdmissionNotificationSender.class);
    private final AdmissionNotificationDispatcher dispatcher =
            new AdmissionNotificationDispatcher(dispatchService, sender);

    @Test
    void asyncKickoffOnlyUsesInitialDeliveryPath() {
        dispatcher.onAdmissionNotificationQueued(new AdmissionNotificationQueued(1L));
        verify(sender).sendInitialAttempt(1L);
        verify(sender, never()).sendScheduledAttempt(anyLong());
    }

    @Test
    void schedulerDelegatesDueNotificationsToApplicationService() {
        dispatcher.sendDueNotifications();
        verify(dispatchService).sendDueNotifications();
        verifyNoInteractions(sender);
    }

    @Test
    void disabledDispatchSkipsBothAsyncKickoffAndScheduledPolling() {
        ReflectionTestUtils.setField(dispatcher, "admissionNotificationsEnabled", false);

        dispatcher.onAdmissionNotificationQueued(new AdmissionNotificationQueued(1L));
        dispatcher.sendDueNotifications();

        verifyNoInteractions(dispatchService, sender);
    }
}
