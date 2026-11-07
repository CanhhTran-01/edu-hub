package com.eduhub.edu_hub.people.infrastructure.admission;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.people.application.admission.*;
import com.eduhub.edu_hub.people.domain.model.AdmissionNotification;
import com.eduhub.edu_hub.people.domain.repository.AdmissionNotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class AdmissionMailDispatcherTest {
    private final AdmissionNotificationRepository notifications = mock(AdmissionNotificationRepository.class);
    private final AdmissionNotificationSender sender = mock(AdmissionNotificationSender.class);
    private final AdmissionMailDispatcher dispatcher = new AdmissionMailDispatcher(notifications, sender);

    @Test
    void asyncKickoffOnlyUsesInitialDeliveryPath() {
        dispatcher.queued(new AdmissionQueued(1L));
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
                        eq(List.of(AdmissionNotification.State.PENDING, AdmissionNotification.State.FAILED)),
                        any(LocalDateTime.class),
                        eq(4),
                        any(Pageable.class));
        verify(sender).send(1L);
        verify(sender).send(2L);
        verify(sender, never()).sendInitial(anyLong());
    }
}
