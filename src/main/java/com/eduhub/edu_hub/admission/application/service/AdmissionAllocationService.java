package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.api.dto.request.AdjustAdmissionAssignmentsRequest;
import com.eduhub.edu_hub.admission.api.dto.request.AdmissionAssignmentRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanResponse;
import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlanClass;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionPlanPolicy;
import com.eduhub.edu_hub.admission.domain.policy.SerpentineClassAllocator;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionApplicantRepository;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanClassRepository;
import com.eduhub.edu_hub.shared.exception.*;
import com.eduhub.edu_hub.shared.systemconfig.application.service.SystemConfigService;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdmissionAllocationService {
    private final AdmissionPlanLockService planLockService;
    private final AdmissionClassPreparationService classPreparationService;
    private final AdmissionPlanClassRepository planClassRepository;
    private final AdmissionApplicantRepository applicantRepository;

    private final SerpentineClassAllocator classAllocator;
    private final AdmissionPlanQueryService planQueryService;
    private final SystemConfigService systemConfigService;

    /**
     * Tự chuẩn bị/phân bổ sau upload hoặc sửa hồ sơ. Cùng transaction với thay đổi đầu vào,
     * không tự tăng revision lần nữa và không tạo Student/User trước khi Admin xác nhận.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void prepareAndAllocateApplicants(AdmissionPlan plan, List<AdmissionApplicant> allApplicants) {
        var pendingApplicants = allApplicants.stream()
                .filter(AdmissionApplicant::isPendingEnrollment)
                .toList();
        var enrolledApplicants = allApplicants.stream()
                .filter(applicant -> applicant.getState() == AdmissionApplicantStatus.ENROLLED)
                .toList();
        plan.setTotalStudents(pendingApplicants.size() + enrolledApplicants.size());
        allApplicants.stream()
                .filter(applicant -> applicant.getState() != AdmissionApplicantStatus.ENROLLED)
                .forEach(applicant -> applicant.setAdministrativeClassId(null));
        if (pendingApplicants.isEmpty()) {
            plan.setState(enrolledApplicants.isEmpty() ? AdmissionPlanStatus.DRAFT : AdmissionPlanStatus.CONFIRMED);
            return;
        }
        // chuẩn bị dữ liệu: Các “chỗ lớp” của kế hoạch, toàn bộ thí sinh, thí sinh đang chờ, thí sinh đã phân bổ
        var plannedClasses = classPreparationService.prepareClassesAutomatically(plan, allApplicants);
        if (plannedClasses.isEmpty()) {
            // Không có phương án trong phạm vi định danh; giữ hồ sơ để Admin xem lỗi, chưa cho xác nhận.
            plan.setState(AdmissionPlanStatus.DRAFT);
            return;
        }
        List<AdmissionPlanClass> remainingClassCapacities = new ArrayList<>();
        for (AdmissionPlanClass plannedClass : plannedClasses) {
            long enrolledCount = enrolledApplicants.stream()
                    .filter(applicant -> Objects.equals(
                            applicant.getAdministrativeClassId(), plannedClass.getAdministrativeClassId()))
                    .count();
            // Chỉ phân vào số chỗ còn lại; không chuyển lớp những người đã nhập học thành công.
            remainingClassCapacities.add(AdmissionPlanClass.builder()
                    .administrativeClassId(plannedClass.getAdministrativeClassId())
                    .targetSize(plannedClass.getTargetSize() - (int) enrolledCount)
                    .build());
        }
        // Phân bổ và chuyển trạng thái
        int maximumSameName = systemConfigService.getSnapshot().getInteger(SystemConfigKey.ADMISSION_MAXIMUM_SAME_NAME);
        classAllocator.allocateByScore(
                pendingApplicants, remainingClassCapacities, enrolledApplicants, maximumSameName);
        plan.setState(AdmissionPlanStatus.PREVIEWED);
        // Cảnh báo dự phòng/dưới tối thiểu chỉ được chấp nhận bằng request xác nhận của Admin.
    }

    @Transactional
    public AdmissionPlanResponse adjustAssignments(Long planId, AdjustAdmissionAssignmentsRequest request) {

        AdmissionPlan plan = planLockService.lockPlanAtRevision(planId, request.expectedRevision());

        if (plan.getState() != AdmissionPlanStatus.PREVIEWED
                && plan.getState() != AdmissionPlanStatus.PARTIALLY_CONFIRMED) {
            throw new AdmissionPlanException(ErrorCode.ADMISSION_INVALID_STATE);
        }

        List<AdmissionPlanClass> plannedClasses = planClassRepository.findByPlan_IdOrderByPosition(planId);
        List<AdmissionApplicant> planApplicants =
                applicantRepository.findByPlan_IdAndDeletedFalseOrderByRowNumber(planId);
        Set<Long> adjustedApplicantIds = new HashSet<>();

        for (AdmissionAssignmentRequest assignment : request.assignments()) {
            // Chống chuyển trùng một người
            if (!adjustedApplicantIds.add(assignment.applicantId()))
                throw new AdmissionPlanException(ErrorCode.VALIDATION_ERROR);

            // Tìm thí sinh đang chờ
            AdmissionApplicant applicant = planApplicants.stream()
                    .filter(candidate ->
                            candidate.getId().equals(assignment.applicantId()) && candidate.isPendingEnrollment())
                    .findFirst()
                    .orElseThrow(() -> new AdmissionPlanException(ErrorCode.VALIDATION_ERROR));

            // Tìm lớp đích, chỉ trong các lớp của plan
            Long targetClassId = plannedClasses.stream()
                    .map(AdmissionPlanClass::getAdministrativeClassId)
                    .filter(candidate -> candidate.equals(assignment.classId()))
                    .findFirst()
                    .orElseThrow(() -> new AdmissionPlanException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE));

            // Gán lớp mới ***
            applicant.setAdministrativeClassId(targetClassId);
        }

        // Kiểm tra sức chứa sau khi chuyển
        for (AdmissionPlanClass plannedClass : plannedClasses) {
            // rows.stream() trên bộ nhớ Java, không query DB, nên không cần flush khi gán lớp mới (chỗ đánh ***)
            long projectedClassSize = planApplicants.stream()
                    .filter(AdmissionApplicant::isIncludedInPlan)
                    .filter(applicant -> applicant.getAdministrativeClassId() != null
                            && applicant.getAdministrativeClassId().equals(plannedClass.getAdministrativeClassId()))
                    .count();

            if (projectedClassSize > plan.getMaxCapacity() + plan.getReserveCapacity()) {
                throw new AdmissionPlanException(ErrorCode.CLASS_CAPACITY_EXCEEDED);
            }
        }

        AdmissionPlanPolicy.incrementRevision(plan);

        // TODO(AUDIT_LOG): Ghi người điều chỉnh, lớp trước/sau của từng hồ sơ khi transaction đã commit.
        log.info("Adjusted admission assignment: planId={}, moved={}", planId, adjustedApplicantIds.size());

        return planQueryService.buildPlanResponse(plan);
    }
}
