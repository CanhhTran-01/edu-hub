package com.eduhub.edu_hub.admission.application.service;

import static com.eduhub.edu_hub.admission.domain.policy.AdmissionNotificationPolicy.MAX_DELIVERY_ATTEMPTS;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionNotificationKind;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionNotificationStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionNotification;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionNotificationRepository;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.enums.AccountNotificationStatus;
import com.eduhub.edu_hub.shared.exception.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
    private final PeopleFacade.Admission people;
    // TODO(NOTIFICATION_MODULE): Chỉ tách sang module notification khi người dùng yêu cầu.
    // Hiện giữ gửi async, scheduler và một lần đầu + ba retry; chưa gọi notification-service bên ngoài.

    /** Only the post-commit async kickoff calls this. A delayed kickoff cannot perform a retry. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendInitial(Long id) {
        AdmissionNotification row = notifications.findForUpdate(id).orElse(null);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (row == null
                || row.getState() != AdmissionNotificationStatus.PENDING
                || row.getFailureCount() != 0
                || row.getNextAttemptAt().isAfter(now)) return;
        deliver(row, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(Long id) {

        AdmissionNotification row = notifications.findForUpdate(id).orElse(null);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        if (row == null
                || row.getState() == AdmissionNotificationStatus.SENT
                || row.getState() == AdmissionNotificationStatus.SKIPPED
                || row.getFailureCount() >= MAX_DELIVERY_ATTEMPTS
                || row.getNextAttemptAt().isAfter(now)) return;

        deliver(row, now);
    }

    private void deliver(AdmissionNotification row, LocalDateTime now) {

        try {
            var student = people.findStudentsByIds(java.util.Set.of(row.getStudentId()))
                    .getFirst();
            var result = row.getKind() == AdmissionNotificationKind.INITIAL_ACCOUNT
                    ? identity.sendStudentAccountNotification(student.getUserId(), "12345")
                    : identity.sendStudentAdmissionNotification(student.getUserId(), row.getRecipient());

            if (result.emailSent()) {
                row.setState(AdmissionNotificationStatus.SENT);
                row.setLastError(null);

            } else {
                failed(row, result.emailError(), now);
            }

        } catch (RuntimeException exception) {
            failed(row, "EMAIL_SEND_FAILED", now);
        }

        people.updateNotificationStatus(
                row.getStudentId(),
                AccountNotificationStatus.valueOf(row.getState().name()),
                row.getLastError());

        // TODO(AUDIT_LOG): Lưu lần gửi đầu, từng retry do scheduler, lỗi và kết quả hết lượt sau commit.
        // Giữ một lần đầu + ba retry; không thêm thao tác thủ công/reset bộ đếm để vượt chính sách.

        if (row.getState() == AdmissionNotificationStatus.FAILED && row.getFailureCount() == MAX_DELIVERY_ATTEMPTS) {
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

    private void failed(AdmissionNotification row, String error, LocalDateTime now) {
        row.setState(AdmissionNotificationStatus.FAILED);
        row.setFailureCount(row.getFailureCount() + 1);
        row.setLastError(error == null ? "EMAIL_SEND_FAILED" : error.substring(0, Math.min(255, error.length())));
        row.setNextAttemptAt(now.plusMinutes(1));
    }
}
