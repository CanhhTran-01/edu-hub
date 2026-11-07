package com.eduhub.edu_hub.notification.infrastructure.dispatch;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.notification.application.event.NotificationQueued;
import com.eduhub.edu_hub.notification.application.service.QueuedNotificationSender;
import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.notification.domain.repository.QueuedNotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class NotificationDispatcherTest {
    private final QueuedNotificationRepository notifications = mock(QueuedNotificationRepository.class);
    private final QueuedNotificationSender sender = mock(QueuedNotificationSender.class);
    private final NotificationDispatcher dispatcher = new NotificationDispatcher(notifications, sender);

    @Test
    void asyncKickoffOnlyUsesInitialDeliveryPath() {
        dispatcher.queued(new NotificationQueued(1L));
        verify(sender).sendInitial(1L);
        verify(sender, never()).send(anyLong());
    }

    @Test
    void schedulerSelectsPendingAndFailedBelowFourFailuresThenCallsSender() {
        when(notifications.findDue(anyList(), any(LocalDateTime.class), eq(4), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L));
        dispatcher.retryDue();
        verify(notifications)
                .findDue(
                        eq(List.of(NotificationStatus.PENDING, NotificationStatus.FAILED)),
                        any(LocalDateTime.class),
                        eq(4),
                        any(Pageable.class));
        verify(sender).send(1L);
        verify(sender).send(2L);
        verify(sender, never()).sendInitial(anyLong());
    }
}
