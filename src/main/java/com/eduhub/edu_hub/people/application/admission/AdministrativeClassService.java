package com.eduhub.edu_hub.people.application.admission;

import com.eduhub.edu_hub.people.api.dto.request.AdmissionRequests.CreateClassesRequest;
import com.eduhub.edu_hub.people.api.dto.response.AdministrativeClassResponse;
import com.eduhub.edu_hub.people.api.dto.response.AdmissionResponses.PlanResponse;
import com.eduhub.edu_hub.people.application.code.AdministrativeClassNames;
import com.eduhub.edu_hub.people.domain.model.*;
import com.eduhub.edu_hub.people.domain.repository.*;
import com.eduhub.edu_hub.scheduling.application.SchedulingFacade;
import com.eduhub.edu_hub.shared.enums.TrainingType;
import com.eduhub.edu_hub.shared.exception.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdministrativeClassService {
    private final AdmissionPlanRepository plans;
    private final AdmissionPlanClassRepository planClasses;
    private final AdministrativeClassRepository classes;
    private final CohortRepository cohorts;
    private final StudentRepository students;
    private final AdmissionCapacityPolicy capacity;
    private final SchedulingFacade.RoomCapacity rooms;
    private final AdmissionView view;
    private final AdmissionApplicantRepository applicants;

    public List<AdministrativeClassResponse> list(Long majorId, Long cohortId) {
        return classes.findByMajor_IdAndCohort_IdOrderById(majorId, cohortId).stream()
                .filter(value -> !value.isDeleted())
                .map(value -> AdministrativeClassResponse.builder()
                        .id(value.getId())
                        .className(value.getClassName())
                        .majorCode(value.getMajor().getMajorCode())
                        .majorName(value.getMajor().getMajorName())
                        .cohortCode(value.getCohort().getCohortCode())
                        .trainingType(value.getTrainingType())
                        .currentSize((int) students.countByAdministrativeClass_IdAndDeletedFalse(value.getId()))
                        .minCapacity(value.getMinCapacity())
                        .maxCapacity(value.getMaxCapacity())
                        .reserveCapacity(value.getReserveCapacity())
                        .availableForAdmission(available(value))
                        .build())
                .toList();
    }

    @Transactional
    public PlanResponse createFromPlan(CreateClassesRequest request) {

        AdmissionPlan plan = AdmissionWorkflowSupport.locked(plans, request.planId(), request.expectedRevision());

        AdmissionWorkflowSupport.requireEditable(plan);

        if (applicants.findByPlan_IdOrderByRowNumber(plan.getId()).stream()
                .anyMatch(row -> row.getState() == AdmissionApplicant.State.ENROLLED)) {
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
        }

        if (request.classCount() < 1 || request.classCount() > 99 || plan.getTotalStudents() == 0) {
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        }

        long roomCount = rooms.countSuitableRooms(plan.getMaxCapacity() + plan.getReserveCapacity());

        var scenario = capacity
                .scenarios(
                        plan.getTotalStudents(),
                        plan.getMinCapacity(),
                        plan.getMaxCapacity(),
                        plan.getReserveCapacity(),
                        roomCount)
                .stream()
                .filter(value -> value.classCount() == request.classCount())
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_INVALID_CAPACITY));

        capacity.requireAcceptance(scenario, request.acceptReserve(), request.acceptBelowMinimum(), request.reason());

        cohorts.findForUpdate(plan.getCohort().getId())
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_STUDENT_CODE_COHORT));

        List<AdmissionPlanClass> previous = planClasses.findByPlan_IdOrderByPosition(plan.getId());

        planClasses.deleteAll(previous);
        planClasses.flush();

        List<AdministrativeClass> scope = classes.findByMajor_IdAndCohort_IdOrderById(
                plan.getMajor().getId(), plan.getCohort().getId());

        List<AdministrativeClass> selected = new ArrayList<>();
        if (request.existingClassIds() != null) {

            if (new HashSet<>(request.existingClassIds()).size()
                            != request.existingClassIds().size()
                    || request.existingClassIds().size() > request.classCount()) {
                throw new BaseException(ErrorCode.VALIDATION_ERROR);
            }

            for (Long id : request.existingClassIds()) {
                AdministrativeClass value = scope.stream()
                        .filter(item -> item.getId().equals(id))
                        .findFirst()
                        .orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE));
                if (!available(value)) throw new BaseException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE);
                selected.add(classes.findByIdForStudentCodeUpdate(id)
                        .orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE)));
                if (students.countByAdministrativeClass_IdAndDeletedFalse(id) > 0) {
                    throw new BaseException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE);
                }
            }

        } else {
            for (AdministrativeClass value : scope) {

                if (selected.size() == request.classCount()) break;
                if (available(value)) {

                    var locked =
                            classes.findByIdForStudentCodeUpdate(value.getId()).orElseThrow();

                    if (students.countByAdministrativeClass_IdAndDeletedFalse(value.getId()) == 0) selected.add(locked);
                }
            }
        }

        // Include deleted and legacy classes so an allocated class number is never reused.
        int next = scope.stream()
                        .map(value -> AdministrativeClassNames.sequence(
                                value.getClassName(), plan.getCohort().getCodeSequence()))
                        .filter(OptionalInt::isPresent)
                        .mapToInt(OptionalInt::getAsInt)
                        .max()
                        .orElse(0)
                + 1;

        while (selected.size() < request.classCount()) {

            if (next > 99) throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_CLASS);

            AdministrativeClass value = new AdministrativeClass();

            value.setClassName(AdministrativeClassNames.create(
                    plan.getMajor().getMajorName(), plan.getCohort().getCodeSequence(), next++));
            value.setMajor(plan.getMajor());
            value.setCohort(plan.getCohort());
            value.setTrainingType(TrainingType.CHINH_QUY);
            value.setMinCapacity(plan.getMinCapacity());
            value.setMaxCapacity(plan.getMaxCapacity());
            value.setReserveCapacity(plan.getReserveCapacity());

            selected.add(classes.save(value));
        }

        for (int index = 0; index < selected.size(); index++) {

            AdministrativeClass value = selected.get(index);

            value.setMinCapacity(plan.getMinCapacity());
            value.setMaxCapacity(plan.getMaxCapacity());
            value.setReserveCapacity(plan.getReserveCapacity());

            AdmissionPlanClass slot = new AdmissionPlanClass();

            slot.setPlan(plan);
            slot.setAdministrativeClass(value);
            slot.setPosition(index + 1);
            slot.setTargetSize(scenario.targetSizes().get(index));

            planClasses.save(slot);
        }

        planClasses.flush();

        plan.setSelectedClassCount(request.classCount());
        plan.setExceptionReason(AdmissionText.optional(request.reason()));
        plan.setState(AdmissionPlan.State.CLASSES_READY);

        applicants.findByPlan_IdOrderByRowNumber(plan.getId()).forEach(row -> row.setAdministrativeClass(null));

        AdmissionWorkflowSupport.changed(plan);

        // TODO(AUDIT_LOG): Record chosen scenario, reused/created classes and exception acceptance.

        log.info(
                "Prepared administrative classes: planId={}, classCount={}, reserveUsed={}",
                plan.getId(),
                request.classCount(),
                scenario.reserveUsed());

        return view.response(plan);
    }

    private boolean available(AdministrativeClass value) {
        return !value.isDeleted()
                && value.getTrainingType() == TrainingType.CHINH_QUY
                && AdministrativeClassNames.sequence(
                                value.getClassName(), value.getCohort().getCodeSequence())
                        .isPresent()
                && value.getLastStudentSequence() == 0
                && !planClasses.existsByAdministrativeClass_Id(value.getId())
                && students.countByAdministrativeClass_IdAndDeletedFalse(value.getId()) == 0;
    }
}
