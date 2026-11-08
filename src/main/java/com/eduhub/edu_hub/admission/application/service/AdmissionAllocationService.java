package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.api.dto.request.AdjustAdmissionAssignmentsRequest;
import com.eduhub.edu_hub.admission.api.dto.request.AdmissionAssignmentRequest;
import com.eduhub.edu_hub.admission.api.dto.request.PreviewAdmissionAllocationRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanResponse;
import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlanClass;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionCapacityPolicy;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionPlanPolicy;
import com.eduhub.edu_hub.admission.domain.policy.SerpentineClassAllocator;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionApplicantRepository;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanClassRepository;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.shared.exception.*;
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
    private final AdmissionPlanClassRepository planClassRepository;
    private final AdmissionApplicantRepository applicantRepository;

    private final PeopleFacade.Admission peopleAdmissionFacade;
    private final AdmissionCapacityPolicy capacityPolicy;
    private final SerpentineClassAllocator classAllocator;
    private final AdmissionPlanQueryService planQueryService;

    @Transactional
    public AdmissionPlanResponse previewAllocation(Long planId, PreviewAdmissionAllocationRequest request) {

        AdmissionPlan plan = planLockService.lockPlanAtRevision(planId, request.expectedRevision()); // xử lý concurrent
        AdmissionPlanPolicy.requireEditable(plan); // phải ở trạng thái còn cho phép sửa

        // chuẩn bị dữ liệu: Các “chỗ lớp” của kế hoạch, toàn bộ thí sinh, thí sinh đang chờ, thí sinh đã phân bổ
        List<AdmissionPlanClass> plannedClasses = planClassRepository.findByPlan_IdOrderByPosition(planId);
        List<AdmissionApplicant> allApplicants = applicantRepository.findByPlan_IdOrderByRowNumber(planId);
        List<AdmissionApplicant> pendingApplicants = allApplicants.stream()
                .filter(AdmissionApplicant::isPendingEnrollment)
                .toList();
        List<AdmissionApplicant> enrolledApplicants = allApplicants.stream()
                .filter(applicant -> applicant.getState() == AdmissionApplicantStatus.ENROLLED)
                .toList();

        if (plannedClasses.isEmpty()
                || pendingApplicants.isEmpty()
                || pendingApplicants.size() + enrolledApplicants.size() != plan.getTotalStudents()) {
            // dữ liệu lệch so với lúc import
            throw new AdmissionPlanException(ErrorCode.ADMISSION_INVALID_STATE);
        }

        // Tính sĩ số mục tiêu mỗi lớp
        List<Integer> targetClassSizes =
                capacityPolicy.distributeEvenly(plan.getTotalStudents(), plannedClasses.size());
        if (targetClassSizes.getFirst() > plan.getMaxCapacity() + plan.getReserveCapacity()) {
            throw new AdmissionPlanException(ErrorCode.ADMISSION_INVALID_CAPACITY);
        }

        List<AdmissionPlanClass> remainingClassCapacities = new ArrayList<>();

        for (int index = 0; index < plannedClasses.size(); index++) {

            var plannedClass = plannedClasses.get(index);

            var administrativeClass = peopleAdmissionFacade.lockClass(plannedClass.getAdministrativeClassId());

            long enrolledCount = enrolledApplicants.stream()
                    .filter(applicant -> applicant.getAdministrativeClassId().equals(administrativeClass.getId()))
                    .count();

            if (administrativeClass.isDeleted()
                    || administrativeClass.getMaxCapacity() != plan.getMaxCapacity()
                    || administrativeClass.getReserveCapacity() != plan.getReserveCapacity()
                    || administrativeClass.getCurrentSize() != enrolledCount
                    || enrolledCount > targetClassSizes.get(index)) {

                // dữ liệu đã đổi so với lúc cấu hình
                throw new AdmissionPlanException(ErrorCode.ADMISSION_DATA_CHANGED);
            }

            plannedClass.setTargetSize(targetClassSizes.get(index));

            // Chỉ phân vào số chỗ còn lại; không chuyển lớp những người đã nhập học thành công.
            AdmissionPlanClass remainingClassCapacity = AdmissionPlanClass.builder()
                    .administrativeClassId(administrativeClass.getId())
                    .targetSize(targetClassSizes.get(index) - (int) enrolledCount)
                    .build();
            remainingClassCapacities.add(remainingClassCapacity);
        }

        // Phân bổ và chuyển trạng thái
        classAllocator.allocateByScore(pendingApplicants, remainingClassCapacities, enrolledApplicants);
        plan.setState(AdmissionPlanStatus.PREVIEWED);

        AdmissionPlanPolicy.incrementRevision(plan);
        log.info(
                "Previewed admission assignment: planId={}, students={}, classes={}",
                planId,
                pendingApplicants.size(),
                plannedClasses.size());

        return planQueryService.buildPlanResponse(plan);
    }

    @Transactional
    public AdmissionPlanResponse adjustAssignments(Long planId, AdjustAdmissionAssignmentsRequest request) {

        AdmissionPlan plan = planLockService.lockPlanAtRevision(planId, request.expectedRevision());

        if (plan.getState() != AdmissionPlanStatus.PREVIEWED
                && plan.getState() != AdmissionPlanStatus.PARTIALLY_CONFIRMED) {
            throw new AdmissionPlanException(ErrorCode.ADMISSION_INVALID_STATE);
        }

        List<AdmissionPlanClass> plannedClasses = planClassRepository.findByPlan_IdOrderByPosition(planId);
        List<AdmissionApplicant> planApplicants = applicantRepository.findByPlan_IdOrderByRowNumber(planId);
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
