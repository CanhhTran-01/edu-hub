package com.eduhub.edu_hub.admission.domain.policy;

import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlanClass;
import com.eduhub.edu_hub.shared.exception.*;
import java.util.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdmissionPlanPolicy {

    public static void requireEditable(AdmissionPlan plan) {
        if (plan.getState() == AdmissionPlanStatus.CONFIRMED
                || plan.getState() == AdmissionPlanStatus.CONFIRMING
                || plan.getState() == AdmissionPlanStatus.PARTIALLY_CONFIRMED)
            throw new AdmissionPlanException(ErrorCode.ADMISSION_INVALID_STATE);
    }

    public static void incrementRevision(AdmissionPlan plan) {
        plan.setRevision(plan.getRevision() + 1);
    }

    /** Điều kiện hiển thị nút xác nhận; API ghi vẫn phải khóa và kiểm tra lại dữ liệu thật. */
    public static List<String> findConfirmationBlockReasons(
            AdmissionPlan plan, List<AdmissionApplicant> applicants, List<AdmissionPlanClass> plannedClasses) {
        List<String> reasons = new ArrayList<>();
        if (plan.getState() != AdmissionPlanStatus.PREVIEWED
                && plan.getState() != AdmissionPlanStatus.PARTIALLY_CONFIRMED) {
            reasons.add("Phương án chưa ở trạng thái sẵn sàng xác nhận");
        }
        if (applicants.stream().noneMatch(AdmissionApplicant::isPendingEnrollment)) {
            reasons.add("Không có hồ sơ đang chờ nhập học");
        }
        Set<Long> allowedClassIds = new HashSet<>();
        plannedClasses.forEach(plannedClass -> allowedClassIds.add(plannedClass.getAdministrativeClassId()));
        if (allowedClassIds.isEmpty()
                || applicants.stream()
                        .filter(AdmissionApplicant::isIncludedInPlan)
                        .anyMatch(applicant -> !allowedClassIds.contains(applicant.getAdministrativeClassId()))) {
            reasons.add("Còn hồ sơ chưa được phân vào lớp của phương án");
        }
        if (applicants.stream().filter(AdmissionApplicant::isIncludedInPlan).count() != plan.getTotalStudents()) {
            reasons.add("Số hồ sơ không khớp với sĩ số phương án");
        }
        if (plannedClasses.stream()
                .anyMatch(plannedClass -> applicants.stream()
                                .filter(AdmissionApplicant::isIncludedInPlan)
                                .filter(applicant -> Objects.equals(
                                        plannedClass.getAdministrativeClassId(), applicant.getAdministrativeClassId()))
                                .count()
                        > plan.getMaxCapacity() + plan.getReserveCapacity())) {
            reasons.add("Có lớp vượt sức chứa tối đa và dự phòng");
        }
        return List.copyOf(reasons);
    }
}
