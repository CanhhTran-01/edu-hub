package com.eduhub.edu_hub.notification.application.facade.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.notification.api.dto.request.QueueAdmissionNotificationRequest;
import com.eduhub.edu_hub.notification.api.dto.response.AdmissionNotificationResponse;
import com.eduhub.edu_hub.notification.application.event.AdmissionNotificationQueued;
import com.eduhub.edu_hub.notification.domain.enums.AdmissionNotificationKind;
import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.notification.domain.model.QueuedAdmissionNotification;
import com.eduhub.edu_hub.notification.domain.repository.QueuedAdmissionNotificationRepository;
import com.eduhub.edu_hub.shared.config.TimeConfig;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class AdmissionNotificationQueueFacadeImplTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-18T17:30:00Z"),
            new TimeConfig().applicationClock().getZone());
    private final QueuedAdmissionNotificationRepository notifications =
            mock(QueuedAdmissionNotificationRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final com.eduhub.edu_hub.people.application.facade.PeopleFacade.Admission people =
            mock(com.eduhub.edu_hub.people.application.facade.PeopleFacade.Admission.class);
    private final AdmissionNotificationQueueFacadeImpl facade = new AdmissionNotificationQueueFacadeImpl(
            notifications,
            events,
            CLOCK,
            com.eduhub.edu_hub.shared.systemconfig.SystemConfigTestData.configuredService(),
            people);

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
        assertThat(facade.enqueueAdmissionNotification(QueueAdmissionNotificationRequest.builder()
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
        assertThat(captor.getValue().getNextAttemptAt())
                .isEqualTo(java.time.LocalDateTime.parse("2026-11-19T00:30:00"));
        verify(events).publishEvent(new AdmissionNotificationQueued(12L));
    }

    @Test
    void missingEmailIsSkippedWithoutConsumingRetries() {
        facade.enqueueAdmissionNotification(QueueAdmissionNotificationRequest.builder()
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
        assertThat(result).extracting(AdmissionNotificationResponse::id).containsExactly(5L, 9L);
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
                        new QueueAdmissionNotificationRequest(2L, 3L, "student@example.com", false)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(events);
    }

    @Test
    void countsRetryWaitingSeparatelyFromTerminalFailures() {
        when(notifications.countDeliveryStates(List.of(2L)))
                .thenReturn(List.of(
                        deliveryCount(NotificationStatus.SENT, 0, 80),
                        deliveryCount(NotificationStatus.PENDING, 0, 10),
                        deliveryCount(NotificationStatus.FAILED, 1, 5),
                        deliveryCount(NotificationStatus.FAILED, 4, 2),
                        deliveryCount(NotificationStatus.SKIPPED, 0, 3)));
        var progress = facade.getNotificationProgress(7L, List.of(2L), false, true);
        assertThat(progress.totalNotifications()).isEqualTo(100);
        assertThat(progress.retryWaitingCount()).isEqualTo(5);
        assertThat(progress.permanentlyFailedCount()).isEqualTo(2);
        assertThat(progress.completedCount()).isEqualTo(85);
        assertThat(progress.completionPercentage()).isEqualByComparingTo("85");
        assertThat(progress.isComplete()).isFalse();
        verifyNoInteractions(events, people);
    }

    @Test
    void finishesProcessingEvenWhenSomeNotificationsExhaustTheirRetries() {
        when(notifications.countDeliveryStates(List.of(2L)))
                .thenReturn(List.of(
                        deliveryCount(NotificationStatus.SENT, 0, 3),
                        deliveryCount(NotificationStatus.FAILED, 4, 2),
                        deliveryCount(NotificationStatus.SKIPPED, 0, 1)));
        var progress = facade.getNotificationProgress(7L, List.of(2L), false, true);
        assertThat(progress.isComplete()).isTrue();
        assertThat(progress.completionPercentage()).isEqualByComparingTo("100");
        assertThat(progress.sentCount()).isEqualTo(3);
        assertThat(progress.permanentlyFailedCount()).isEqualTo(2);
    }

    @Test
    void cannotFinishWhileConfirmationCanStillCreateMoreNotifications() {
        when(notifications.countDeliveryStates(List.of(2L)))
                .thenReturn(List.of(deliveryCount(NotificationStatus.SENT, 0, 1)));
        assertThat(facade.getNotificationProgress(7L, List.of(2L), true, false).isComplete())
                .isFalse();
    }

    @Test
    void distinguishesNotStartedFromACompletedConfirmationWithNoNotifications() {
        assertThat(facade.getNotificationProgress(7L, List.of(), false, false).isComplete())
                .isFalse();
        var noEmails = facade.getNotificationProgress(7L, List.of(), false, true);
        assertThat(noEmails.isComplete()).isTrue();
        assertThat(noEmails.totalNotifications()).isZero();
        assertThat(noEmails.completionPercentage()).isEqualByComparingTo("0");
        verify(notifications, never()).countDeliveryStates(anyList());
    }

    @Test
    void reportsPausedDispatchWithoutTreatingPendingMailAsComplete() {
        var pausedFacade = new AdmissionNotificationQueueFacadeImpl(
                notifications,
                events,
                CLOCK,
                com.eduhub.edu_hub.shared.systemconfig.SystemConfigTestData.configuredService(java.util.Map.of(
                        com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey
                                .ADMISSION_NOTIFICATIONS_ENABLED,
                        "false")),
                people);
        when(notifications.countDeliveryStates(List.of(2L)))
                .thenReturn(List.of(deliveryCount(NotificationStatus.PENDING, 0, 1)));
        var progress = pausedFacade.getNotificationProgress(7L, List.of(2L), false, true);
        assertThat(progress.dispatchEnabled()).isFalse();
        assertThat(progress.isComplete()).isFalse();
        verifyNoInteractions(events);
    }

    @Test
    void pagesDetailsAndExposesNextAttemptOnlyForNonTerminalMail() {
        var notification = QueuedAdmissionNotification.builder()
                .admissionId(2L)
                .studentId(3L)
                .state(NotificationStatus.FAILED)
                .failureCount(1)
                .nextAttemptAt(java.time.LocalDateTime.of(2026, 11, 24, 10, 0))
                .build();
        notification.setId(12L);
        when(notifications.findAll(
                        any(org.springframework.data.jpa.domain.Specification.class),
                        any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(notification)));
        when(people.findStudentsByIds(java.util.Set.of(3L)))
                .thenReturn(List.of(com.eduhub.edu_hub.people.application.facade.dto.AdmissionStudentInfo.builder()
                        .id(3L)
                        .fullName("Nguyễn Văn An")
                        .studentCode("CNTT080101")
                        .build()));
        var request = new com.eduhub.edu_hub.notification.api.dto.request.SearchAdmissionNotificationsRequest();
        var response = facade.searchNotifications(List.of(2L), request);
        assertThat(response.getContent()).singleElement().satisfies(detail -> {
            assertThat(detail.fullName()).isEqualTo("Nguyễn Văn An");
            assertThat(detail.deliveryState())
                    .isEqualTo(com.eduhub.edu_hub.notification.domain.enums.NotificationDeliveryState.WAITING_RETRY);
            assertThat(detail.nextAttemptAt()).isNotNull();
        });
        notification.setState(NotificationStatus.SENT);
        assertThat(facade.searchNotifications(List.of(2L), request)
                        .getContent()
                        .getFirst()
                        .nextAttemptAt())
                .isNull();
    }

    private QueuedAdmissionNotificationRepository.DeliveryStateCount deliveryCount(
            NotificationStatus state, int failureCount, long count) {
        return new QueuedAdmissionNotificationRepository.DeliveryStateCount() {
            public NotificationStatus getState() {
                return state;
            }

            public int getFailureCount() {
                return failureCount;
            }

            public long getNotificationCount() {
                return count;
            }
        };
    }
}
