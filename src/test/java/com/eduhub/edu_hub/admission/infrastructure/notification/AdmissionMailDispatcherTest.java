package com.eduhub.edu_hub.admission.infrastructure.notification;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.admission.application.event.AdmissionNotificationQueued;
import com.eduhub.edu_hub.admission.application.service.AdmissionNotificationSender;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionNotificationStatus;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionNotificationRepository;
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
        dispatcher.queued(new AdmissionNotificationQueued(1L));
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
                        eq(List.of(AdmissionNotificationStatus.PENDING, AdmissionNotificationStatus.FAILED)),
                        any(LocalDateTime.class),
                        eq(4),
                        any(Pageable.class));
        verify(sender).send(1L);
        verify(sender).send(2L);
        verify(sender, never()).sendInitial(anyLong());
    }
}
