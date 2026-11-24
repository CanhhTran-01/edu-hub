package com.eduhub.edu_hub.notification.application.facade.impl;

import com.eduhub.edu_hub.notification.api.dto.request.QueueAdmissionNotificationRequest;
import com.eduhub.edu_hub.notification.api.dto.request.SearchAdmissionNotificationsRequest;
import com.eduhub.edu_hub.notification.api.dto.response.AdmissionNotificationDetailResponse;
import com.eduhub.edu_hub.notification.api.dto.response.AdmissionNotificationProgressResponse;
import com.eduhub.edu_hub.notification.api.dto.response.AdmissionNotificationResponse;
import com.eduhub.edu_hub.notification.application.event.AdmissionNotificationQueued;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.notification.application.specification.AdmissionNotificationSpecification;
import com.eduhub.edu_hub.notification.domain.enums.AdmissionNotificationKind;
import com.eduhub.edu_hub.notification.domain.enums.NotificationDeliveryState;
import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.notification.domain.model.QueuedAdmissionNotification;
import com.eduhub.edu_hub.notification.domain.policy.NotificationRetryPolicy;
import com.eduhub.edu_hub.notification.domain.repository.QueuedAdmissionNotificationRepository;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.AdmissionStudentInfo;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.systemconfig.application.service.SystemConfigService;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdmissionNotificationQueueFacadeImpl implements NotificationFacade.AdmissionQueue {
    private static final int QUERY_BATCH_SIZE = 500;
    private final QueuedAdmissionNotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final SystemConfigService systemConfigService;
    private final PeopleFacade.Admission peopleAdmissionFacade;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Long enqueueAdmissionNotification(QueueAdmissionNotificationRequest request) {
        // Tham gia transaction của bên gọi: không lưu hàng đợi hay gửi mail nếu nhập học rollback.
        QueuedAdmissionNotification queuedNotification = createQueuedNotification(request);
        notificationRepository.saveAndFlush(queuedNotification);

        eventPublisher.publishEvent(new AdmissionNotificationQueued(queuedNotification.getId()));
        return queuedNotification.getId();
    }

    private QueuedAdmissionNotification createQueuedNotification(QueueAdmissionNotificationRequest request) {
        return QueuedAdmissionNotification.builder()
                .admissionId(request.admissionId())
                .studentId(request.studentId())
                .recipient(request.recipient())
                .nextAttemptAt(LocalDateTime.now(clock))
                .kind(
                        request.reEnrollment()
                                ? AdmissionNotificationKind.RE_ENROLLMENT
                                : AdmissionNotificationKind.INITIAL_ACCOUNT)
                .state(request.recipient() == null ? NotificationStatus.SKIPPED : NotificationStatus.PENDING)
                .lastError(request.recipient() == null ? "NO_EMAIL" : null)
                .build();
    }

    @Override
    public List<AdmissionNotificationResponse> findNotificationsByAdmissionIds(List<Long> admissionIds) {

        var distinctAdmissionIds = admissionIds.stream().distinct().toList();
        List<AdmissionNotificationResponse> notificationResponses = new ArrayList<>();

        for (int offset = 0; offset < distinctAdmissionIds.size(); offset += QUERY_BATCH_SIZE) {
            notificationRepository
                    .findByAdmissionIdInAndDeletedFalseOrderById(distinctAdmissionIds.subList(
                            offset, Math.min(offset + QUERY_BATCH_SIZE, distinctAdmissionIds.size())))
                    .stream()
                    .map(this::toAdmissionNotificationResponse)
                    .forEach(notificationResponses::add);
        }
        notificationResponses.sort(Comparator.comparing(AdmissionNotificationResponse::id));
        return List.copyOf(notificationResponses);
    }

    private AdmissionNotificationResponse toAdmissionNotificationResponse(QueuedAdmissionNotification notification) {
        return AdmissionNotificationResponse.builder()
                .id(notification.getId())
                .admissionId(notification.getAdmissionId())
                .state(notification.getState().name())
                .failureCount(notification.getFailureCount())
                .error(notification.getLastError())
                .build();
    }

    @Override
    public AdmissionNotificationProgressResponse getNotificationProgress(
            Long planId, List<Long> admissionIds, boolean confirmationInProgress, boolean confirmationFinished) {
        var distinctAdmissionIds = admissionIds.stream().distinct().toList();
        Map<NotificationDeliveryState, Long> counts = new EnumMap<>(NotificationDeliveryState.class);
        for (int offset = 0; offset < distinctAdmissionIds.size(); offset += QUERY_BATCH_SIZE) {
            notificationRepository
                    .countDeliveryStates(distinctAdmissionIds.subList(
                            offset, Math.min(offset + QUERY_BATCH_SIZE, distinctAdmissionIds.size())))
                    .forEach(deliveryCount -> counts.merge(
                            NotificationRetryPolicy.classifyDeliveryState(
                                    deliveryCount.getState(), deliveryCount.getFailureCount()),
                            deliveryCount.getNotificationCount(),
                            Long::sum));
        }
        long sent = counts.getOrDefault(NotificationDeliveryState.SENT, 0L);
        long pending = counts.getOrDefault(NotificationDeliveryState.PENDING, 0L);
        long retryWaiting = counts.getOrDefault(NotificationDeliveryState.WAITING_RETRY, 0L);
        long permanentlyFailed = counts.getOrDefault(NotificationDeliveryState.PERMANENTLY_FAILED, 0L);
        long skipped = counts.getOrDefault(NotificationDeliveryState.SKIPPED, 0L);
        long completed = sent + permanentlyFailed + skipped;
        long total = completed + pending + retryWaiting;
        BigDecimal percentage = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(completed)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        boolean isComplete = !confirmationInProgress && completed == total && (total > 0 || confirmationFinished);
        return new AdmissionNotificationProgressResponse(
                planId,
                total,
                sent,
                pending,
                retryWaiting,
                permanentlyFailed,
                skipped,
                completed,
                percentage,
                isComplete,
                systemConfigService.getSnapshot().getBoolean(SystemConfigKey.ADMISSION_NOTIFICATIONS_ENABLED));
    }

    @Override
    public PageResponse<AdmissionNotificationDetailResponse> searchNotifications(
            List<Long> admissionIds, SearchAdmissionNotificationsRequest request) {
        if (admissionIds.isEmpty())
            return PageResponse.<AdmissionNotificationDetailResponse>builder()
                    .content(List.of())
                    .pageNumber(request.getPage())
                    .pageSize(request.getSize())
                    .isLast(true)
                    .build();
        var notificationPage = notificationRepository.findAll(
                AdmissionNotificationSpecification.inAdmissions(
                        admissionIds.stream().distinct().toList(), request.getDeliveryState()),
                PageRequest.of(
                        request.getPage(), request.getSize(), Sort.by("id").ascending()));
        Set<Long> studentIds = notificationPage.getContent().stream()
                .map(QueuedAdmissionNotification::getStudentId)
                .collect(Collectors.toSet());
        Map<Long, AdmissionStudentInfo> studentsById = studentIds.isEmpty()
                ? Map.of()
                : peopleAdmissionFacade.findStudentsByIds(studentIds).stream()
                        .collect(Collectors.toMap(AdmissionStudentInfo::getId, student -> student));
        var details = notificationPage.getContent().stream()
                .map(notification -> toNotificationDetail(notification, studentsById))
                .toList();
        return PageResponse.<AdmissionNotificationDetailResponse>builder()
                .content(details)
                .pageNumber(notificationPage.getNumber())
                .pageSize(notificationPage.getSize())
                .totalElements(notificationPage.getTotalElements())
                .totalPages(notificationPage.getTotalPages())
                .isLast(notificationPage.isLast())
                .build();
    }

    private AdmissionNotificationDetailResponse toNotificationDetail(
            QueuedAdmissionNotification notification, Map<Long, AdmissionStudentInfo> studentsById) {
        // Chỉ bản ghi còn chờ mới có lần gửi kế tiếp; trạng thái hiển thị không sửa trạng thái lưu DB.
        var student = studentsById.get(notification.getStudentId());
        var deliveryState =
                NotificationRetryPolicy.classifyDeliveryState(notification.getState(), notification.getFailureCount());
        boolean hasNextAttempt = deliveryState == NotificationDeliveryState.PENDING
                || deliveryState == NotificationDeliveryState.WAITING_RETRY;
        return new AdmissionNotificationDetailResponse(
                notification.getId(),
                notification.getAdmissionId(),
                notification.getStudentId(),
                student == null ? null : student.getFullName(),
                student == null ? null : student.getStudentCode(),
                deliveryState,
                notification.getFailureCount(),
                hasNextAttempt ? notification.getNextAttemptAt() : null,
                notification.getLastError(),
                notification.getUpdatedAt());
    }
}
