package com.eduhub.edu_hub.people.application.admission;

import static com.eduhub.edu_hub.people.domain.policy.AdmissionNotificationPolicy.MAX_DELIVERY_ATTEMPTS;

import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.response.AdmissionResponses.NotificationResponse;
import com.eduhub.edu_hub.people.domain.enums.AccountNotificationStatus;
import com.eduhub.edu_hub.people.domain.model.AdmissionNotification;
import com.eduhub.edu_hub.people.domain.repository.AdmissionNotificationRepository;
import com.eduhub.edu_hub.shared.exception.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdmissionNotificationSender {
    private final AdmissionNotificationRepository notifications;
    private final IdentityFacade identity;

    /** Only the post-commit async kickoff calls this. A delayed kickoff cannot perform a retry. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendInitial(Long id) {
        AdmissionNotification row = notifications.findForUpdate(id).orElse(null);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (row == null
                || row.getState() != AdmissionNotification.State.PENDING
                || row.getFailureCount() != 0
                || row.getNextAttemptAt().isAfter(now)) return;
        deliver(row, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(Long id) {

        AdmissionNotification row = notifications.findForUpdate(id).orElse(null);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        if (row == null
                || row.getState() == AdmissionNotification.State.SENT
                || row.getState() == AdmissionNotification.State.SKIPPED
                || row.getFailureCount() >= MAX_DELIVERY_ATTEMPTS
                || row.getNextAttemptAt().isAfter(now)) return;

        deliver(row, now);
    }

    private void deliver(AdmissionNotification row, LocalDateTime now) {

        try {
            var result = row.getKind() == AdmissionNotification.Kind.INITIAL_ACCOUNT
                    ? identity.sendStudentAccountNotification(row.getStudent().getUserId(), "12345")
                    : identity.sendStudentAdmissionNotification(row.getStudent().getUserId(), row.getRecipient());

            if (result.emailSent()) {
                row.setState(AdmissionNotification.State.SENT);
                row.setLastError(null);

            } else {
                failed(row, result.emailError(), now);
            }

        } catch (RuntimeException exception) {
            failed(row, "EMAIL_SEND_FAILED", now);
        }

        row.getStudent()
                .setAccountNotificationStatus(
                        AccountNotificationStatus.valueOf(row.getState().name()));
        row.getStudent().setAccountNotificationError(row.getLastError());

        // TODO(AUDIT_LOG): persist each initial/scheduled attempt, error and terminal failure.
        // Three retries follow the initial attempt; never add a manual reset/retry bypass.

        if (row.getState() == AdmissionNotification.State.FAILED && row.getFailureCount() == MAX_DELIVERY_ATTEMPTS) {
            log.warn(
                    "Admission email permanently failed after initial delivery and 3 retries: notificationId={}, error={}",
                    row.getId(),
                    row.getLastError());
        } else {
            log.info(
                    "Admission notification: notificationId={}, state={}, failures={}",
                    row.getId(),
                    row.getState(),
                    row.getFailureCount());
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(Long planId) {
        return notifications.findByAdmission_Plan_IdOrderById(planId).stream()
                .map(this::response)
                .toList();
    }

    private void failed(AdmissionNotification row, String error, LocalDateTime now) {
        row.setState(AdmissionNotification.State.FAILED);
        row.setFailureCount(row.getFailureCount() + 1);
        row.setLastError(error == null ? "EMAIL_SEND_FAILED" : error.substring(0, Math.min(255, error.length())));
        row.setNextAttemptAt(now.plusMinutes(1));
    }

    private NotificationResponse response(AdmissionNotification row) {
        return new NotificationResponse(
                row.getId(),
                row.getAdmission().getId(),
                row.getState().name(),
                row.getFailureCount(),
                row.getLastError());
    }
}
