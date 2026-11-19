package com.eduhub.edu_hub.notification.infrastructure.dispatch;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.notification.application.event.AdmissionNotificationQueued;
import com.eduhub.edu_hub.notification.application.service.AdmissionNotificationDispatchService;
import com.eduhub.edu_hub.notification.application.service.QueuedAdmissionNotificationSender;
import com.eduhub.edu_hub.shared.systemconfig.SystemConfigTestData;
import com.eduhub.edu_hub.shared.systemconfig.application.service.SystemConfigService;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import org.junit.jupiter.api.Test;

class AdmissionNotificationDispatcherTest {
    private final AdmissionNotificationDispatchService dispatchService =
            mock(AdmissionNotificationDispatchService.class);
    private final QueuedAdmissionNotificationSender sender = mock(QueuedAdmissionNotificationSender.class);
    private final SystemConfigService systemConfigService = SystemConfigTestData.configuredService();
    private final AdmissionNotificationDispatcher dispatcher =
            new AdmissionNotificationDispatcher(dispatchService, sender, systemConfigService);

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
        when(systemConfigService.getSnapshot())
                .thenReturn(SystemConfigTestData.snapshot(
                        java.util.Map.of(SystemConfigKey.ADMISSION_NOTIFICATIONS_ENABLED, "false")));

        dispatcher.onAdmissionNotificationQueued(new AdmissionNotificationQueued(1L));
        dispatcher.sendDueNotifications();

        verifyNoInteractions(dispatchService, sender);
    }
}
