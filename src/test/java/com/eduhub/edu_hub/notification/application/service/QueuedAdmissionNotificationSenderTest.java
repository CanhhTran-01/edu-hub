package com.eduhub.edu_hub.notification.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.eduhub.edu_hub.identity.api.dto.response.AccountNotificationResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.notification.domain.enums.AdmissionNotificationKind;
import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.notification.domain.model.QueuedAdmissionNotification;
import com.eduhub.edu_hub.notification.domain.repository.QueuedAdmissionNotificationRepository;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.AdmissionStudentInfo;
import com.eduhub.edu_hub.people.domain.enums.AccountNotificationStatus;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class QueuedAdmissionNotificationSenderTest {
    private final QueuedAdmissionNotificationRepository notifications =
            mock(QueuedAdmissionNotificationRepository.class);
    private final IdentityFacade identity = mock(IdentityFacade.class);
    private final PeopleFacade.Admission people = mock(PeopleFacade.Admission.class);
    private final QueuedAdmissionNotificationSender sender =
            new QueuedAdmissionNotificationSender(notifications, identity, people);

    @Test
    void stopsAfterFourFailedDeliveriesAndLogsTerminalFailureOnlyOnce() {
        QueuedAdmissionNotification row = pending();
        when(identity.sendStudentAccountNotification(10L))
                .thenReturn(new AccountNotificationResponse(10L, false, "EMAIL_SEND_FAILED"));
        Logger logger = (Logger) LoggerFactory.getLogger(QueuedAdmissionNotificationSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            sender.sendInitialAttempt(1L);
            assertThat(row.getFailureCount()).isEqualTo(1);
            for (int retry = 1; retry <= 3; retry++) {
                row.setNextAttemptAt(LocalDateTime.MIN);
                sender.sendScheduledAttempt(1L);
                assertThat(row.getFailureCount()).isEqualTo(1 + retry);
            }
            row.setNextAttemptAt(LocalDateTime.MIN);
            sender.sendScheduledAttempt(1L);
            sender.sendInitialAttempt(1L);
            assertThat(row.getState()).isEqualTo(NotificationStatus.FAILED);
            assertThat(row.getFailureCount()).isEqualTo(4);
            verify(identity, times(4)).sendStudentAccountNotification(10L);
            assertThat(appender.list.stream()
                            .filter(event -> event.getLevel() == Level.WARN
                                    && event.getFormattedMessage().contains("permanently failed"))
                            .count())
                    .isEqualTo(1);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void lateAsyncKickoffCannotRetryAnAlreadyFailedInitialDelivery() {
        var row = pending();
        when(identity.sendStudentAccountNotification(10L))
                .thenReturn(new AccountNotificationResponse(10L, false, "EMAIL_SEND_FAILED"));
        sender.sendInitialAttempt(1L);
        row.setNextAttemptAt(LocalDateTime.MIN);
        sender.sendInitialAttempt(1L);
        assertThat(row.getFailureCount()).isEqualTo(1);
        verify(identity).sendStudentAccountNotification(10L);
    }

    @Test
    void thirdRetryCanSucceedWithoutResettingFailureHistory() {
        var row = pending();
        row.setState(NotificationStatus.FAILED);
        row.setFailureCount(3);
        when(identity.sendStudentAccountNotification(10L)).thenReturn(new AccountNotificationResponse(10L, true, null));
        sender.sendScheduledAttempt(1L);
        sender.sendScheduledAttempt(1L);
        assertThat(row.getState()).isEqualTo(NotificationStatus.SENT);
        assertThat(row.getFailureCount()).isEqualTo(3);
        verify(identity).sendStudentAccountNotification(10L);
    }

    @Test
    void respectsNextAttemptTimeAndSkippedNotifications() {
        var row = pending();
        row.setNextAttemptAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
        sender.sendScheduledAttempt(1L);
        sender.sendInitialAttempt(1L);
        row.setState(NotificationStatus.SKIPPED);
        row.setNextAttemptAt(LocalDateTime.MIN);
        sender.sendScheduledAttempt(1L);
        verifyNoInteractions(identity);
    }

    @Test
    void reEnrollmentSendsConfirmationAndSynchronizesStatusWithoutSendingAnInitialPassword() {
        var notification = pending();
        notification.setKind(AdmissionNotificationKind.RE_ENROLLMENT);
        notification.setRecipient("returning@example.com");
        when(identity.sendStudentAdmissionNotification(10L, "returning@example.com"))
                .thenReturn(new AccountNotificationResponse(10L, true, null));

        sender.sendInitialAttempt(1L);

        assertThat(notification.getState()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getFailureCount()).isZero();
        assertThat(notification.getLastError()).isNull();
        verify(identity).sendStudentAdmissionNotification(10L, "returning@example.com");
        verifyNoMoreInteractions(identity);
        verify(people).updateNotificationStatus(42L, AccountNotificationStatus.SENT, null);
    }

    @Test
    void scheduledAttemptRecoversPendingNotificationsWhoseAsyncKickoffWasLost() {
        var notification = pending();
        when(identity.sendStudentAccountNotification(10L)).thenReturn(new AccountNotificationResponse(10L, true, null));

        sender.sendScheduledAttempt(1L);

        assertThat(notification.getState()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getFailureCount()).isZero();
        verify(people).updateNotificationStatus(42L, AccountNotificationStatus.SENT, null);
    }

    @Test
    void deliveryExceptionSchedulesRetryAndSynchronizesTheFailureWithoutLoggingSensitiveContext() {
        var notification = pending();
        when(identity.sendStudentAccountNotification(10L))
                .thenThrow(new IllegalStateException("sensitive account details"));
        Logger logger = (Logger) LoggerFactory.getLogger(QueuedAdmissionNotificationSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            LocalDateTime beforeAttempt = LocalDateTime.now(ZoneOffset.UTC);
            sender.sendInitialAttempt(1L);
            LocalDateTime afterAttempt = LocalDateTime.now(ZoneOffset.UTC);

            assertThat(notification.getState()).isEqualTo(NotificationStatus.FAILED);
            assertThat(notification.getFailureCount()).isEqualTo(1);
            assertThat(notification.getLastError()).isEqualTo("EMAIL_SEND_FAILED");
            assertThat(notification.getNextAttemptAt())
                    .isBetween(beforeAttempt.plusMinutes(1), afterAttempt.plusMinutes(1));
            verify(people).updateNotificationStatus(42L, AccountNotificationStatus.FAILED, "EMAIL_SEND_FAILED");
            assertThat(appender.list).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("sensitive account details");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void missingNotificationDoesNotLookUpOrNotifyAnyStudent() {
        sender.sendInitialAttempt(1L);
        sender.sendScheduledAttempt(1L);
        verifyNoInteractions(identity, people);
    }

    private QueuedAdmissionNotification pending() {
        when(people.findStudentsByIds(java.util.Set.of(42L)))
                .thenReturn(java.util.List.of(
                        AdmissionStudentInfo.builder().id(42L).userId(10L).build()));
        QueuedAdmissionNotification row = QueuedAdmissionNotification.builder().build();
        row.setId(1L);
        row.setStudentId(42L);
        row.setKind(AdmissionNotificationKind.INITIAL_ACCOUNT);
        row.setState(NotificationStatus.PENDING);
        row.setNextAttemptAt(LocalDateTime.MIN);
        when(notifications.findByIdForUpdate(1L)).thenReturn(Optional.of(row));
        return row;
    }
}
