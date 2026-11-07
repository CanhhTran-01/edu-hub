package com.eduhub.edu_hub.notification.application.facade.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.notification.application.command.AdmissionNotificationCommand;
import com.eduhub.edu_hub.notification.application.event.NotificationQueued;
import com.eduhub.edu_hub.notification.application.facade.dto.AdmissionNotificationInfo;
import com.eduhub.edu_hub.notification.domain.enums.NotificationKind;
import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.notification.domain.model.QueuedNotification;
import com.eduhub.edu_hub.notification.domain.repository.QueuedNotificationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class QueueNotificationFacadeImplTest {
    private final QueuedNotificationRepository notifications = mock(QueuedNotificationRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final QueueNotificationFacadeImpl facade = new QueueNotificationFacadeImpl(notifications, events);

    @BeforeEach
    void assignId() {
        when(notifications.saveAndFlush(any())).thenAnswer(call -> {
            QueuedNotification row = call.getArgument(0);
            row.setId(12L);
            return row;
        });
    }

    @Test
    void storesOnlySourceIdsAndQueuesInitialAccountDelivery() {
        assertThat(facade.queueAdmission(AdmissionNotificationCommand.builder()
                        .admissionId(2L)
                        .studentId(3L)
                        .recipient("student@example.com")
                        .build()))
                .isEqualTo(12L);
        var captor = ArgumentCaptor.forClass(QueuedNotification.class);
        verify(notifications).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getAdmissionId()).isEqualTo(2L);
        assertThat(captor.getValue().getStudentId()).isEqualTo(3L);
        assertThat(captor.getValue().getKind()).isEqualTo(NotificationKind.INITIAL_ACCOUNT);
        assertThat(captor.getValue().getState()).isEqualTo(NotificationStatus.PENDING);
        assertThat(captor.getValue().getFailureCount()).isZero();
        verify(events).publishEvent(new NotificationQueued(12L));
    }

    @Test
    void missingEmailIsSkippedWithoutConsumingRetries() {
        facade.queueAdmission(AdmissionNotificationCommand.builder()
                .admissionId(2L)
                .studentId(3L)
                .reEnrollment(true)
                .build());
        var captor = ArgumentCaptor.forClass(QueuedNotification.class);
        verify(notifications).saveAndFlush(captor.capture());
        var row = captor.getValue();
        assertThat(row.getKind()).isEqualTo(NotificationKind.RE_ENROLLMENT);
        assertThat(row.getState()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(row.getLastError()).isEqualTo("NO_EMAIL");
        assertThat(row.getFailureCount()).isZero();
    }

    @Test
    void batchesSourceIdsAndReturnsNotificationsInStableIdOrder() {
        var ids = new ArrayList<>(LongStream.rangeClosed(1, 501).boxed().toList());
        ids.add(1L);
        var firstBatch = QueuedNotification.builder()
                .admissionId(1L)
                .state(NotificationStatus.SENT)
                .build();
        firstBatch.setId(9L);
        var secondBatch = QueuedNotification.builder()
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
        var result = facade.findByAdmissionIds(ids);
        assertThat(result).extracting(AdmissionNotificationInfo::id).containsExactly(5L, 9L);
        assertThat(result.getFirst().failureCount()).isEqualTo(4);
        assertThat(result.getFirst().error()).isEqualTo("EMAIL_SEND_FAILED");
        verify(notifications, times(2)).findByAdmissionIdInAndDeletedFalseOrderById(anyList());
    }

    @Test
    void emptySourceListDoesNotQueryOrSendAnything() {
        assertThat(facade.findByAdmissionIds(List.of())).isEmpty();
        verify(notifications, never()).findByAdmissionIdInAndDeletedFalseOrderById(anyList());
        verifyNoInteractions(events);
    }
}
