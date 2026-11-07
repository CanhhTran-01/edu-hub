package com.eduhub.edu_hub.notification.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.application.result.CreateStudentAccountResult;
import com.eduhub.edu_hub.notification.domain.enums.NotificationKind;
import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.notification.domain.model.QueuedNotification;
import com.eduhub.edu_hub.notification.domain.repository.QueuedNotificationRepository;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.AdmissionStudentInfo;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class QueuedNotificationSenderTest {
    private final QueuedNotificationRepository notifications = mock(QueuedNotificationRepository.class);
    private final IdentityFacade identity = mock(IdentityFacade.class);
    private final PeopleFacade.Admission people = mock(PeopleFacade.Admission.class);
    private final QueuedNotificationSender sender = new QueuedNotificationSender(notifications, identity, people);

    @Test
    void stopsAfterFourFailedDeliveriesAndLogsTerminalFailureOnlyOnce() {
        QueuedNotification row = pending();
        when(identity.sendStudentAccountNotification(10L, "12345"))
                .thenReturn(new CreateStudentAccountResult(10L, false, "EMAIL_SEND_FAILED"));
        Logger logger = (Logger) LoggerFactory.getLogger(QueuedNotificationSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            sender.sendInitial(1L);
            assertThat(row.getFailureCount()).isEqualTo(1);
            for (int retry = 1; retry <= 3; retry++) {
                row.setNextAttemptAt(LocalDateTime.MIN);
                sender.send(1L);
                assertThat(row.getFailureCount()).isEqualTo(1 + retry);
            }
            row.setNextAttemptAt(LocalDateTime.MIN);
            sender.send(1L);
            sender.sendInitial(1L);
            assertThat(row.getState()).isEqualTo(NotificationStatus.FAILED);
            assertThat(row.getFailureCount()).isEqualTo(4);
            verify(identity, times(4)).sendStudentAccountNotification(10L, "12345");
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
        when(identity.sendStudentAccountNotification(10L, "12345"))
                .thenReturn(new CreateStudentAccountResult(10L, false, "EMAIL_SEND_FAILED"));
        sender.sendInitial(1L);
        row.setNextAttemptAt(LocalDateTime.MIN);
        sender.sendInitial(1L);
        assertThat(row.getFailureCount()).isEqualTo(1);
        verify(identity).sendStudentAccountNotification(10L, "12345");
    }

    @Test
    void thirdRetryCanSucceedWithoutResettingFailureHistory() {
        var row = pending();
        row.setState(NotificationStatus.FAILED);
        row.setFailureCount(3);
        when(identity.sendStudentAccountNotification(10L, "12345"))
                .thenReturn(new CreateStudentAccountResult(10L, true, null));
        sender.send(1L);
        sender.send(1L);
        assertThat(row.getState()).isEqualTo(NotificationStatus.SENT);
        assertThat(row.getFailureCount()).isEqualTo(3);
        verify(identity).sendStudentAccountNotification(10L, "12345");
    }

    @Test
    void respectsNextAttemptTimeAndSkippedNotifications() {
        var row = pending();
        row.setNextAttemptAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
        sender.send(1L);
        sender.sendInitial(1L);
        row.setState(NotificationStatus.SKIPPED);
        row.setNextAttemptAt(LocalDateTime.MIN);
        sender.send(1L);
        verifyNoInteractions(identity);
    }

    private QueuedNotification pending() {
        when(people.findStudentsByIds(java.util.Set.of(42L)))
                .thenReturn(java.util.List.of(
                        AdmissionStudentInfo.builder().id(42L).userId(10L).build()));
        QueuedNotification row = QueuedNotification.builder().build();
        row.setId(1L);
        row.setStudentId(42L);
        row.setKind(NotificationKind.INITIAL_ACCOUNT);
        row.setState(NotificationStatus.PENDING);
        row.setNextAttemptAt(LocalDateTime.MIN);
        when(notifications.findForUpdate(1L)).thenReturn(Optional.of(row));
        return row;
    }
}
