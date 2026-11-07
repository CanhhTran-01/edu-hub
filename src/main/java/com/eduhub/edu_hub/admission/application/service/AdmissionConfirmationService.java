package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.ConfirmCommand;
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
import com.eduhub.edu_hub.shared.text.PersonText;
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
    private final AdmissionPlanRepository plans;
    private final AdmissionPlanClassRepository planClasses;
    private final AdmissionApplicantRepository applicants;
    private final PeopleFacade.Admission people;
    private final AdmissionPlanQueryService view;
    private final ApplicantEnrollmentService enrollment;
    private final AdmissionProcessingLease lease;
    private final TransactionTemplate transactions;

    public ConfirmationResult confirm(Long id, ConfirmCommand request) {
        Work work = transaction(() -> prepare(id, request));
        // Mỗi hồ sơ một transaction: một người lỗi không rollback những người đã nhập học thành công.
        for (Long applicantId : work.applicantIds()) {
            try {
                transaction(() -> {
                    enrollment.enroll(id, work.token(), applicantId);
                    return true;
                });
            } catch (RuntimeException exception) {
                if (exception instanceof BaseException base && base.getErrorCode() == ErrorCode.ADMISSION_INVALID_STATE)
                    throw exception;
                transaction(() -> {
                    var plan = lease.require(id, work.token());
                    var row = applicants.findById(applicantId).orElseThrow();
                    if (row.isPending()) {
                        row.setState(AdmissionApplicantStatus.FAILED);
                        row.setErrorMessage(
                                exception instanceof BaseException base
                                        ? base.getErrorCode().name()
                                        : "Không thể lưu hồ sơ; có thể thử lại");
                    }
                    lease.renew(plan);
                    return true;
                });
                log.warn(
                        "Admission row failed: planId={}, applicantId={}, failureType={}",
                        id,
                        applicantId,
                        exception.getClass().getSimpleName());
            }
        }
        return transaction(() -> finish(id, work.token(), request));
    }

    private Work prepare(Long id, ConfirmCommand request) {
        var plan = plans.findForUpdate(id).orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_PLAN_NOT_FOUND));
        if (plan.getState() == AdmissionPlanStatus.CONFIRMED) return new Work(null, List.of());
        if (plan.getRevision() != request.expectedRevision()) throw new BaseException(ErrorCode.ADMISSION_STALE_PLAN);
        boolean expired = plan.getState() == AdmissionPlanStatus.CONFIRMING
                && plan.getProcessingUntil() != null
                && !plan.getProcessingUntil().isAfter(LocalDateTime.now(ZoneOffset.UTC));
        if (plan.getState() != AdmissionPlanStatus.PREVIEWED
                && plan.getState() != AdmissionPlanStatus.PARTIALLY_CONFIRMED
                && !expired) throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
        var rows = applicants.findByPlan_IdOrderByRowNumber(id);
        var slots = planClasses.findByPlan_IdOrderByPosition(id);
        if (slots.isEmpty() || rows.stream().noneMatch(AdmissionApplicant::isIncluded))
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
        validateProjected(plan, rows, slots, request);
        Map<Long, Integer> positions = new HashMap<>();
        slots.forEach(s -> positions.put(s.getAdministrativeClassId(), s.getPosition()));
        var ids = rows.stream()
                .filter(AdmissionApplicant::isPending)
                .sorted(Comparator.comparingInt((AdmissionApplicant r) -> positions.get(r.getAdministrativeClassId()))
                        .thenComparing(AdmissionApplicant::getFullName, PersonText.nameOrder())
                        .thenComparing(AdmissionApplicant::getApplicationCode))
                .map(AdmissionApplicant::getId)
                .toList();
        String token = UUID.randomUUID().toString();
        plan.setConfirmationToken(token);
        plan.setState(AdmissionPlanStatus.CONFIRMING);
        plan.setExceptionReason(PersonText.optional(request.reason()));
        AdmissionPlanPolicy.changed(plan);
        lease.renew(plan);
        return new Work(token, ids);
    }

    private void validateProjected(
            AdmissionPlan plan, List<AdmissionApplicant> rows, List<AdmissionPlanClass> slots, ConfirmCommand request) {
        var allowed = slots.stream()
                .map(AdmissionPlanClass::getAdministrativeClassId)
                .collect(java.util.stream.Collectors.toSet());
        if (rows.stream()
                .filter(AdmissionApplicant::isIncluded)
                .anyMatch(r -> r.getAdministrativeClassId() == null || !allowed.contains(r.getAdministrativeClassId())))
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
        for (var slot : slots.stream()
                .sorted(Comparator.comparing(AdmissionPlanClass::getAdministrativeClassId))
                .toList()) {
            var target = people.lockClass(slot.getAdministrativeClassId());
            if (target.isDeleted()
                    || target.getMaxCapacity() != plan.getMaxCapacity()
                    || target.getReserveCapacity() != plan.getReserveCapacity()
                    || !target.getMajorId().equals(plan.getMajorId())
                    || !target.getCohortId().equals(plan.getCohortId()))
                throw new BaseException(ErrorCode.ADMISSION_DATA_CHANGED);
            long projected = rows.stream()
                    .filter(AdmissionApplicant::isIncluded)
                    .filter(r -> target.getId().equals(r.getAdministrativeClassId()))
                    .count();
            long own = rows.stream()
                    .filter(r -> r.getState() == AdmissionApplicantStatus.ENROLLED)
                    .filter(r -> target.getId().equals(r.getAdministrativeClassId()))
                    .count();
            if (target.getCurrentSize() != own) throw new BaseException(ErrorCode.ADMISSION_DATA_CHANGED);
            if (projected > plan.getMaxCapacity() + plan.getReserveCapacity())
                throw new BaseException(ErrorCode.CLASS_CAPACITY_EXCEEDED);
            if ((projected > plan.getMaxCapacity() && !request.acceptReserve())
                    || (projected < plan.getMinCapacity()
                            && (!request.acceptBelowMinimum() || PersonText.optional(request.reason()) == null)))
                throw new BaseException(ErrorCode.ADMISSION_WARNING_CONFIRMATION_REQUIRED);
        }
    }

    private ConfirmationResult finish(Long id, String token, ConfirmCommand request) {
        var plan = plans.findForUpdate(id).orElseThrow();
        var rows = applicants.findByPlan_IdOrderByRowNumber(id);
        if (token != null) {
            lease.require(plan, token);
            boolean pending = rows.stream().anyMatch(AdmissionApplicant::isPending);
            boolean under = !request.acceptBelowMinimum()
                    && planClasses.findByPlan_IdOrderByPosition(id).stream()
                            .anyMatch(s -> rows.stream()
                                            .filter(AdmissionApplicant::isIncluded)
                                            .filter(r ->
                                                    s.getAdministrativeClassId().equals(r.getAdministrativeClassId()))
                                            .count()
                                    < plan.getMinCapacity());
            plan.setState(pending || under ? AdmissionPlanStatus.PARTIALLY_CONFIRMED : AdmissionPlanStatus.CONFIRMED);
            plan.setTotalStudents(
                    (int) rows.stream().filter(AdmissionApplicant::isIncluded).count());
            plan.setConfirmationToken(null);
            plan.setProcessingUntil(null);
            AdmissionPlanPolicy.changed(plan);
            log.info("Finished admission confirmation: planId={}, state={}", id, plan.getState());
        }
        return ConfirmationResult.builder()
                .planId(id)
                .revision(plan.getRevision())
                .state(plan.getState().name())
                .enrolled((int) rows.stream()
                        .filter(r -> r.getState() == AdmissionApplicantStatus.ENROLLED)
                        .count())
                .failed((int) rows.stream()
                        .filter(r -> r.getState() == AdmissionApplicantStatus.FAILED)
                        .count())
                .skipped((int) rows.stream()
                        .filter(r -> r.getState() == AdmissionApplicantStatus.EXISTING)
                        .count())
                .applicants(view.applicantResults(rows))
                .build();
    }

    private <T> T transaction(Supplier<T> work) {
        return Objects.requireNonNull(transactions.execute(status -> work.get()));
    }

    private record Work(String token, List<Long> applicantIds) {}
}
