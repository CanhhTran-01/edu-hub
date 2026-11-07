package com.eduhub.edu_hub.people.application.admission;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.application.result.CreateStudentAccountResult;
import com.eduhub.edu_hub.people.domain.model.*;
import com.eduhub.edu_hub.people.domain.repository.AdmissionNotificationRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AdmissionNotificationSenderTest {
    private final AdmissionNotificationRepository notifications = mock(AdmissionNotificationRepository.class);
    private final IdentityFacade identity = mock(IdentityFacade.class);
    private final AdmissionNotificationSender sender = new AdmissionNotificationSender(notifications, identity);

    @Test
    void stopsAfterFourFailedDeliveriesAndLogsTerminalFailureOnlyOnce() {
        AdmissionNotification row = pending();
        when(identity.sendStudentAccountNotification(10L, "12345"))
                .thenReturn(new CreateStudentAccountResult(10L, false, "EMAIL_SEND_FAILED"));
        Logger logger = (Logger) LoggerFactory.getLogger(AdmissionNotificationSender.class);
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
            assertThat(row.getState()).isEqualTo(AdmissionNotification.State.FAILED);
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
        row.setState(AdmissionNotification.State.FAILED);
        row.setFailureCount(3);
        when(identity.sendStudentAccountNotification(10L, "12345"))
                .thenReturn(new CreateStudentAccountResult(10L, true, null));
        sender.send(1L);
        sender.send(1L);
        assertThat(row.getState()).isEqualTo(AdmissionNotification.State.SENT);
        assertThat(row.getFailureCount()).isEqualTo(3);
        verify(identity).sendStudentAccountNotification(10L, "12345");
    }

    @Test
    void respectsNextAttemptTimeAndSkippedNotifications() {
        var row = pending();
        row.setNextAttemptAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
        sender.send(1L);
        sender.sendInitial(1L);
        row.setState(AdmissionNotification.State.SKIPPED);
        row.setNextAttemptAt(LocalDateTime.MIN);
        sender.send(1L);
        verifyNoInteractions(identity);
    }

    private AdmissionNotification pending() {
        Student student = new Student();
        student.setUserId(10L);
        AdmissionNotification row = new AdmissionNotification();
        row.setId(1L);
        row.setStudent(student);
        row.setKind(AdmissionNotification.Kind.INITIAL_ACCOUNT);
        row.setState(AdmissionNotification.State.PENDING);
        row.setNextAttemptAt(LocalDateTime.MIN);
        when(notifications.findForUpdate(1L)).thenReturn(Optional.of(row));
        return row;
    }
}
