package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.api.dto.request.PrepareAdmissionClassesRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdministrativeClassResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanResponse;
import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlanClass;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionCapacityPolicy;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionPlanPolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionApplicantRepository;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanClassRepository;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.*;
import com.eduhub.edu_hub.scheduling.application.SchedulingFacade;
import com.eduhub.edu_hub.shared.exception.*;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdmissionClassPreparationService {
    private final AdmissionPlanLockService planLockService;
    private final AdmissionPlanClassRepository planClassRepository;
    private final PeopleFacade.Admission peopleAdmissionFacade;
    private final AdmissionCapacityPolicy capacityPolicy;
    private final SchedulingFacade.RoomCapacity roomCapacityFacade;
    private final AdmissionPlanQueryService planQueryService;
    private final AdmissionApplicantRepository applicantRepository;

    public List<AdministrativeClassResponse> listAdministrativeClasses(Long majorId, Long cohortId) {
        return peopleAdmissionFacade.findClasses(majorId, cohortId).stream()
                .filter(administrativeClass -> !administrativeClass.isDeleted())
                .map(administrativeClass -> AdministrativeClassResponse.builder()
                        .id(administrativeClass.getId())
                        .className(administrativeClass.getClassName())
                        .majorCode(administrativeClass.getMajorCode())
                        .majorName(administrativeClass.getMajorName())
                        .cohortCode(administrativeClass.getCohortCode())
                        .trainingType(administrativeClass.getTrainingType())
                        .currentSize((int) administrativeClass.getCurrentSize())
                        .minCapacity(administrativeClass.getMinCapacity())
                        .maxCapacity(administrativeClass.getMaxCapacity())
                        .reserveCapacity(administrativeClass.getReserveCapacity())
                        .availableForAdmission(isAvailableForAdmission(administrativeClass))
                        .build())
                .toList();
    }

    @Transactional
    public AdmissionPlanResponse prepareClasses(PrepareAdmissionClassesRequest request) {
        AdmissionPlan plan = planLockService.lockPlanAtRevision(request.planId(), request.expectedRevision());
        AdmissionPlanPolicy.requireEditable(plan);
        if (applicantRepository.findByPlan_IdOrderByRowNumber(plan.getId()).stream()
                .anyMatch(applicant -> applicant.getState() == AdmissionApplicantStatus.ENROLLED))
            throw new AdmissionPlanException(ErrorCode.ADMISSION_INVALID_STATE);
        if (plan.getTotalStudents() == 0)
            throw new AdmissionPlanException(ErrorCode.VALIDATION_ERROR);
        var scenario = capacityPolicy
                .generateScenarios(
                        plan.getTotalStudents(),
                        plan.getMinCapacity(),
                        plan.getMaxCapacity(),
                        plan.getReserveCapacity(),
                        roomCapacityFacade.countSuitableRooms(plan.getMaxCapacity() + plan.getReserveCapacity()))
                .stream()
                .filter(candidateScenario -> candidateScenario.classCount() == request.classCount())
                .findFirst()
                .orElseThrow(() -> new AdmissionPlanException(ErrorCode.ADMISSION_INVALID_CAPACITY));
        capacityPolicy.requireScenarioAcceptance(
                scenario, request.acceptReserve(), request.acceptBelowMinimum(), request.reason());
        peopleAdmissionFacade.lockCohort(plan.getCohortId());
        planClassRepository.deleteAll(planClassRepository.findByPlan_IdOrderByPosition(plan.getId()));
        planClassRepository.flush();
        List<AdmissionClassInfo> classesInScope =
                peopleAdmissionFacade.findClasses(plan.getMajorId(), plan.getCohortId());
        List<AdmissionClassInfo> selectedClasses = new ArrayList<>();
        if (request.existingClassIds() != null) {
            if (new HashSet<>(request.existingClassIds()).size()
                            != request.existingClassIds().size()
                    || request.existingClassIds().size() > request.classCount())
                throw new AdmissionPlanException(ErrorCode.VALIDATION_ERROR);
            for (Long classId : request.existingClassIds()) {
                AdmissionClassInfo candidateClass = classesInScope.stream()
                        .filter(administrativeClass ->
                                administrativeClass.getId().equals(classId))
                        .findFirst()
                        .orElseThrow(() -> new AdmissionPlanException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE));
                if (!isAvailableForAdmission(candidateClass))
                    throw new AdmissionPlanException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE);
                var lockedClass = peopleAdmissionFacade.lockClass(classId);
                if (lockedClass.getCurrentSize() != 0)
                    throw new AdmissionPlanException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE);
                selectedClasses.add(lockedClass);
            }
        } else {
            for (var candidateClass : classesInScope) {
                if (selectedClasses.size() == request.classCount()) break;
                if (isAvailableForAdmission(candidateClass)) {
                    var lockedClass = peopleAdmissionFacade.lockClass(candidateClass.getId());
                    if (lockedClass.getCurrentSize() == 0) selectedClasses.add(lockedClass);
                }
            }
        }
        // Tính cả lớp đã xóa/tên lớp cũ để không cấp lại số thứ tự đã sử dụng.
        int nextClassSequence = classesInScope.stream()
                        .map(AdmissionClassInfo::getClassSequence)
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .max()
                        .orElse(0)
                + 1;
        while (selectedClasses.size() < request.classCount()) {
            if (nextClassSequence > 99) throw new AdmissionPlanException(ErrorCode.INVALID_STUDENT_CODE_CLASS);
            selectedClasses.add(peopleAdmissionFacade.createClass(AdmissionClassRequest.builder()
                    .majorId(plan.getMajorId())
                    .cohortId(plan.getCohortId())
                    .sequence(nextClassSequence++)
                    .minimum(plan.getMinCapacity())
                    .maximum(plan.getMaxCapacity())
                    .reserve(plan.getReserveCapacity())
                    .build()));
        }
        for (int classIndex = 0; classIndex < selectedClasses.size(); classIndex++) {
            var configuredClass = peopleAdmissionFacade.configureClass(
                    selectedClasses.get(classIndex).getId(),
                    plan.getMinCapacity(),
                    plan.getMaxCapacity(),
                    plan.getReserveCapacity());
            planClassRepository.save(AdmissionPlanClass.builder()
                    .plan(plan)
                    .administrativeClassId(configuredClass.getId())
                    .position(classIndex + 1)
                    .targetSize(scenario.targetSizes().get(classIndex))
                    .build());
        }
        planClassRepository.flush();
        plan.setSelectedClassCount(request.classCount());
        plan.setExceptionReason(TextsNormalizer.normalizeOptionalText(request.reason()));
        plan.setState(AdmissionPlanStatus.CLASSES_READY);
        applicantRepository
                .findByPlan_IdOrderByRowNumber(plan.getId())
                .forEach(applicant -> applicant.setAdministrativeClassId(null));
        AdmissionPlanPolicy.incrementRevision(plan);
        // TODO(AUDIT_LOG): Ghi kịch bản được chọn, lớp tạo/dùng lại và xác nhận ngoại lệ.
        log.info("Prepared administrative classes: planId={}, classCount={}", plan.getId(), request.classCount());
        return planQueryService.buildPlanResponse(plan);
    }

    private boolean isAvailableForAdmission(AdmissionClassInfo administrativeClass) {
        return !administrativeClass.isDeleted()
                && administrativeClass.getTrainingType() == TrainingType.CHINH_QUY
                && administrativeClass.getClassSequence() != null
                && administrativeClass.getLastStudentSequence() == 0
                && administrativeClass.getCurrentSize() == 0
                && !planClassRepository.existsByAdministrativeClassId(administrativeClass.getId());
    }
}
