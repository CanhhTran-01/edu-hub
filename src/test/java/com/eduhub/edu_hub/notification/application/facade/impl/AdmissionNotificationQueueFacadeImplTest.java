package com.eduhub.edu_hub.notification.application.facade.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.notification.application.command.QueueAdmissionNotificationCommand;
import com.eduhub.edu_hub.notification.application.event.AdmissionNotificationQueued;
import com.eduhub.edu_hub.notification.application.facade.dto.AdmissionNotificationInfo;
import com.eduhub.edu_hub.notification.domain.enums.AdmissionNotificationKind;
import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.notification.domain.model.QueuedAdmissionNotification;
import com.eduhub.edu_hub.notification.domain.repository.QueuedAdmissionNotificationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class AdmissionNotificationQueueFacadeImplTest {
    private final QueuedAdmissionNotificationRepository notifications =
            mock(QueuedAdmissionNotificationRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AdmissionNotificationQueueFacadeImpl facade =
            new AdmissionNotificationQueueFacadeImpl(notifications, events);

    @BeforeEach
    void assignId() {
        when(notifications.saveAndFlush(any())).thenAnswer(call -> {
            QueuedAdmissionNotification row = call.getArgument(0);
            row.setId(12L);
            return row;
        });
    }

    @Test
    void storesOnlySourceIdsAndQueuesInitialAccountDelivery() {
        assertThat(facade.enqueueAdmissionNotification(QueueAdmissionNotificationCommand.builder()
                        .admissionId(2L)
                        .studentId(3L)
                        .recipient("student@example.com")
                        .build()))
                .isEqualTo(12L);
        var captor = ArgumentCaptor.forClass(QueuedAdmissionNotification.class);
        verify(notifications).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getAdmissionId()).isEqualTo(2L);
        assertThat(captor.getValue().getStudentId()).isEqualTo(3L);
        assertThat(captor.getValue().getKind()).isEqualTo(AdmissionNotificationKind.INITIAL_ACCOUNT);
        assertThat(captor.getValue().getState()).isEqualTo(NotificationStatus.PENDING);
        assertThat(captor.getValue().getFailureCount()).isZero();
        verify(events).publishEvent(new AdmissionNotificationQueued(12L));
    }

    @Test
    void missingEmailIsSkippedWithoutConsumingRetries() {
        facade.enqueueAdmissionNotification(QueueAdmissionNotificationCommand.builder()
                .admissionId(2L)
                .studentId(3L)
                .reEnrollment(true)
                .build());
        var captor = ArgumentCaptor.forClass(QueuedAdmissionNotification.class);
        verify(notifications).saveAndFlush(captor.capture());
        var row = captor.getValue();
        assertThat(row.getKind()).isEqualTo(AdmissionNotificationKind.RE_ENROLLMENT);
        assertThat(row.getState()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(row.getLastError()).isEqualTo("NO_EMAIL");
        assertThat(row.getFailureCount()).isZero();
    }

    @Test
    void batchesSourceIdsAndReturnsNotificationsInStableIdOrder() {
        var ids = new ArrayList<>(LongStream.rangeClosed(1, 501).boxed().toList());
        ids.add(1L);
        var firstBatch = QueuedAdmissionNotification.builder()
                .admissionId(1L)
                .state(NotificationStatus.SENT)
                .build();
        firstBatch.setId(9L);
        var secondBatch = QueuedAdmissionNotification.builder()
                .admissionId(501L)
                .state(NotificationStatus.FAILED)
                .failureCount(4)
                .lastError("EMAIL_SEND_FAILED")
                .build();
        secondBatch.setId(5L);
        when(notifications.findByAdmissionIdInAndDeletedFalseOrderById(ids.subList(0, 500)))
                .thenReturn(List.of(firstBatch));
        when(notifications.findByAdmissionIdInAndDeletedFalseOrderById(List.of(501L)))
                .thenReturn(List.of(secondBatch));
        var result = facade.findNotificationsByAdmissionIds(ids);
        assertThat(result).extracting(AdmissionNotificationInfo::id).containsExactly(5L, 9L);
        assertThat(result.getFirst().failureCount()).isEqualTo(4);
        assertThat(result.getFirst().error()).isEqualTo("EMAIL_SEND_FAILED");
        verify(notifications, times(2)).findByAdmissionIdInAndDeletedFalseOrderById(anyList());
    }

    @Test
    void emptySourceListDoesNotQueryOrSendAnything() {
        assertThat(facade.findNotificationsByAdmissionIds(List.of())).isEmpty();
        verify(notifications, never()).findByAdmissionIdInAndDeletedFalseOrderById(anyList());
        verifyNoInteractions(events);
    }

    @Test
    void failedQueueInsertDoesNotPublishAnEvent() {
        doThrow(new IllegalStateException("Database unavailable"))
                .when(notifications)
                .saveAndFlush(any());

        assertThatThrownBy(() -> facade.enqueueAdmissionNotification(
                        new QueueAdmissionNotificationCommand(2L, 3L, "student@example.com", false)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(events);
    }
}
