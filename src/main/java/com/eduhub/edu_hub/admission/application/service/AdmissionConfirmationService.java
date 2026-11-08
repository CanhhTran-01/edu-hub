package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.ConfirmEnrollmentCommand;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.ConfirmationResult;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlanClass;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionPlanPolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionApplicantRepository;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanClassRepository;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanRepository;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.shared.exception.*;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class AdmissionConfirmationService {
    private final AdmissionPlanRepository planRepository;
    private final AdmissionPlanClassRepository planClassRepository;
    private final AdmissionApplicantRepository applicantRepository;
    private final PeopleFacade.Admission peopleAdmissionFacade;
    private final AdmissionPlanQueryService planQueryService;
    private final AdmissionApplicantEnrollmentService enrollmentService;
    private final AdmissionConfirmationLease confirmationLease;
    private final TransactionTemplate transactionTemplate;

    public ConfirmationResult confirmEnrollment(Long planId, ConfirmEnrollmentCommand command) {
        ConfirmationBatch confirmationBatch = executeInTransaction(() -> prepareConfirmationBatch(planId, command));
        // Mỗi hồ sơ một transaction: một người lỗi không rollback những người đã nhập học thành công.
        for (Long applicantId : confirmationBatch.applicantIds()) {
            try {
                executeInTransaction(() -> {
                    enrollmentService.enroll(planId, confirmationBatch.confirmationToken(), applicantId);
                    return true;
                });
            } catch (RuntimeException exception) {
                if (exception instanceof BaseException base && base.getErrorCode() == ErrorCode.ADMISSION_INVALID_STATE)
                    throw exception;
                executeInTransaction(() -> {
                    var plan =
                            confirmationLease.requireActiveConfirmation(planId, confirmationBatch.confirmationToken());
                    var applicant = applicantRepository.findById(applicantId).orElseThrow();
                    if (applicant.isPendingEnrollment()) {
                        applicant.setState(AdmissionApplicantStatus.FAILED);
                        applicant.setErrorMessage(
                                exception instanceof BaseException base
                                        ? base.getErrorCode().name()
                                        : "Không thể lưu hồ sơ; có thể thử lại");
                    }
                    confirmationLease.extendConfirmationLease(plan);
                    return true;
                });
                log.warn(
                        "Admission row failed: planId={}, applicantId={}, failureType={}",
                        planId,
                        applicantId,
                        exception.getClass().getSimpleName());
            }
        }
        return executeInTransaction(
                () -> completeConfirmationBatch(planId, confirmationBatch.confirmationToken(), command));
    }

    private ConfirmationBatch prepareConfirmationBatch(Long planId, ConfirmEnrollmentCommand command) {
        var plan = planRepository
                .findForUpdate(planId)
                .orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_PLAN_NOT_FOUND));
        if (plan.getState() == AdmissionPlanStatus.CONFIRMED) return new ConfirmationBatch(null, List.of());
        if (plan.getRevision() != command.expectedRevision()) throw new BaseException(ErrorCode.ADMISSION_STALE_PLAN);
        boolean confirmationLeaseExpired = plan.getState() == AdmissionPlanStatus.CONFIRMING
                && plan.getProcessingUntil() != null
                && !plan.getProcessingUntil().isAfter(LocalDateTime.now(ZoneOffset.UTC));
        if (plan.getState() != AdmissionPlanStatus.PREVIEWED
                && plan.getState() != AdmissionPlanStatus.PARTIALLY_CONFIRMED
                && !confirmationLeaseExpired) throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
        var planApplicants = applicantRepository.findByPlan_IdOrderByRowNumber(planId);
        var plannedClasses = planClassRepository.findByPlan_IdOrderByPosition(planId);
        if (plannedClasses.isEmpty() || planApplicants.stream().noneMatch(AdmissionApplicant::isIncludedInPlan))
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
        validateProjectedClassSizes(plan, planApplicants, plannedClasses, command);
        Map<Long, Integer> classPositionsById = new HashMap<>();
        plannedClasses.forEach(plannedClass ->
                classPositionsById.put(plannedClass.getAdministrativeClassId(), plannedClass.getPosition()));
        var pendingApplicantIds = planApplicants.stream()
                .filter(AdmissionApplicant::isPendingEnrollment)
                .sorted(Comparator.comparingInt((AdmissionApplicant applicant) ->
                                classPositionsById.get(applicant.getAdministrativeClassId()))
                        .thenComparing(AdmissionApplicant::getFullName, TextsNormalizer.vietnameseNameOrder())
                        .thenComparing(AdmissionApplicant::getApplicationCode))
                .map(AdmissionApplicant::getId)
                .toList();
        String confirmationToken = UUID.randomUUID().toString();
        plan.setConfirmationToken(confirmationToken);
        plan.setState(AdmissionPlanStatus.CONFIRMING);
        plan.setExceptionReason(TextsNormalizer.normalizeOptionalText(command.reason()));
        AdmissionPlanPolicy.incrementRevision(plan);
        confirmationLease.extendConfirmationLease(plan);
        return new ConfirmationBatch(confirmationToken, pendingApplicantIds);
    }

    private void validateProjectedClassSizes(
            AdmissionPlan plan,
            List<AdmissionApplicant> planApplicants,
            List<AdmissionPlanClass> plannedClasses,
            ConfirmEnrollmentCommand command) {
        var allowedClassIds = plannedClasses.stream()
                .map(AdmissionPlanClass::getAdministrativeClassId)
                .collect(java.util.stream.Collectors.toSet());
        if (planApplicants.stream()
                .filter(AdmissionApplicant::isIncludedInPlan)
                .anyMatch(applicant -> applicant.getAdministrativeClassId() == null
                        || !allowedClassIds.contains(applicant.getAdministrativeClassId())))
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
        for (var plannedClass : plannedClasses.stream()
                .sorted(Comparator.comparing(AdmissionPlanClass::getAdministrativeClassId))
                .toList()) {
            var administrativeClass = peopleAdmissionFacade.lockClass(plannedClass.getAdministrativeClassId());
            if (administrativeClass.isDeleted()
                    || administrativeClass.getMaxCapacity() != plan.getMaxCapacity()
                    || administrativeClass.getReserveCapacity() != plan.getReserveCapacity()
                    || !administrativeClass.getMajorId().equals(plan.getMajorId())
                    || !administrativeClass.getCohortId().equals(plan.getCohortId()))
                throw new BaseException(ErrorCode.ADMISSION_DATA_CHANGED);
            long projectedClassSize = planApplicants.stream()
                    .filter(AdmissionApplicant::isIncludedInPlan)
                    .filter(applicant -> administrativeClass.getId().equals(applicant.getAdministrativeClassId()))
                    .count();
            long enrolledCount = planApplicants.stream()
                    .filter(applicant -> applicant.getState() == AdmissionApplicantStatus.ENROLLED)
                    .filter(applicant -> administrativeClass.getId().equals(applicant.getAdministrativeClassId()))
                    .count();
            if (administrativeClass.getCurrentSize() != enrolledCount)
                throw new BaseException(ErrorCode.ADMISSION_DATA_CHANGED);
            if (projectedClassSize > plan.getMaxCapacity() + plan.getReserveCapacity())
                throw new BaseException(ErrorCode.CLASS_CAPACITY_EXCEEDED);
            if ((projectedClassSize > plan.getMaxCapacity() && !command.acceptReserve())
                    || (projectedClassSize < plan.getMinCapacity()
                            && (!command.acceptBelowMinimum()
                                    || TextsNormalizer.normalizeOptionalText(command.reason()) == null)))
                throw new BaseException(ErrorCode.ADMISSION_WARNING_CONFIRMATION_REQUIRED);
        }
    }

    private ConfirmationResult completeConfirmationBatch(
            Long planId, String confirmationToken, ConfirmEnrollmentCommand command) {
        var plan = planRepository.findForUpdate(planId).orElseThrow();
        var planApplicants = applicantRepository.findByPlan_IdOrderByRowNumber(planId);
        if (confirmationToken != null) {
            confirmationLease.requireActiveConfirmation(plan, confirmationToken);
            boolean hasPendingApplicants = planApplicants.stream().anyMatch(AdmissionApplicant::isPendingEnrollment);
            boolean hasUnderfilledClasses = !command.acceptBelowMinimum()
                    && planClassRepository.findByPlan_IdOrderByPosition(planId).stream()
                            .anyMatch(plannedClass -> planApplicants.stream()
                                            .filter(AdmissionApplicant::isIncludedInPlan)
                                            .filter(applicant -> plannedClass
                                                    .getAdministrativeClassId()
                                                    .equals(applicant.getAdministrativeClassId()))
                                            .count()
                                    < plan.getMinCapacity());
            plan.setState(
                    hasPendingApplicants || hasUnderfilledClasses
                            ? AdmissionPlanStatus.PARTIALLY_CONFIRMED
                            : AdmissionPlanStatus.CONFIRMED);
            plan.setTotalStudents((int) planApplicants.stream()
                    .filter(AdmissionApplicant::isIncludedInPlan)
                    .count());
            plan.setConfirmationToken(null);
            plan.setProcessingUntil(null);
            AdmissionPlanPolicy.incrementRevision(plan);
            log.info("Finished admission confirmation: planId={}, state={}", planId, plan.getState());
        }
        return ConfirmationResult.builder()
                .planId(planId)
                .revision(plan.getRevision())
                .state(plan.getState().name())
                .enrolled((int) planApplicants.stream()
                        .filter(applicant -> applicant.getState() == AdmissionApplicantStatus.ENROLLED)
                        .count())
                .failed((int) planApplicants.stream()
                        .filter(applicant -> applicant.getState() == AdmissionApplicantStatus.FAILED)
                        .count())
                .skipped((int) planApplicants.stream()
                        .filter(applicant -> applicant.getState() == AdmissionApplicantStatus.EXISTING)
                        .count())
                .applicants(planQueryService.buildApplicantResults(planApplicants))
                .build();
    }

    private <T> T executeInTransaction(Supplier<T> operation) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> operation.get()));
    }

    private record ConfirmationBatch(String confirmationToken, List<Long> applicantIds) {}
}
