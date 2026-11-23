package com.eduhub.edu_hub.notification.application.specification;

import com.eduhub.edu_hub.notification.domain.enums.*;
import com.eduhub.edu_hub.notification.domain.model.QueuedAdmissionNotification;
import com.eduhub.edu_hub.notification.domain.policy.NotificationRetryPolicy;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Chỉ dựng predicate phạm vi/trạng thái trước phân trang, không tải dữ liệu hoặc gọi facade. */
public final class AdmissionNotificationSpecification {
    private AdmissionNotificationSpecification() {}

    public static Specification<QueuedAdmissionNotification> inAdmissions(
            List<Long> admissionIds, NotificationDeliveryState deliveryState) {
        return (notification, query, predicates) -> {
            var scope = predicates.and(
                    predicates.isFalse(notification.get("deleted")),
                    notification.get("admissionId").in(admissionIds));
            if (deliveryState == null) return scope;
            var status = notification.<NotificationStatus>get("state");
            var failures = notification.<Integer>get("failureCount");
            var deliveryPredicate =
                    switch (deliveryState) {
                        case PENDING -> predicates.equal(status, NotificationStatus.PENDING);
                        case SENT -> predicates.equal(status, NotificationStatus.SENT);
                        case SKIPPED -> predicates.equal(status, NotificationStatus.SKIPPED);
                        case WAITING_RETRY -> predicates.and(
                                predicates.equal(status, NotificationStatus.FAILED),
                                predicates.lessThan(failures, NotificationRetryPolicy.MAX_DELIVERY_ATTEMPTS));
                        case PERMANENTLY_FAILED -> predicates.and(
                                predicates.equal(status, NotificationStatus.FAILED),
                                predicates.greaterThanOrEqualTo(
                                        failures, NotificationRetryPolicy.MAX_DELIVERY_ATTEMPTS));
                    };
            return predicates.and(scope, deliveryPredicate);
        };
    }
}
