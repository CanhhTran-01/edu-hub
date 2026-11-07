package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.CreateClassesCommand;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.AdministrativeClassResult;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.PlanResult;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlanClass;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionCapacityPolicy;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionPlanPolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionApplicantRepository;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanClassRepository;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanRepository;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.*;
import com.eduhub.edu_hub.scheduling.application.SchedulingFacade;
import com.eduhub.edu_hub.shared.enums.TrainingType;
import com.eduhub.edu_hub.shared.exception.*;
import com.eduhub.edu_hub.shared.text.PersonText;
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
    private final AdmissionPlanRepository plans;
    private final AdmissionPlanClassRepository planClasses;
    private final PeopleFacade.Admission people;
    private final AdmissionCapacityPolicy capacity;
    private final SchedulingFacade.RoomCapacity rooms;
    private final AdmissionPlanQueryService view;
    private final AdmissionApplicantRepository applicants;

    public List<AdministrativeClassResult> list(Long majorId, Long cohortId) {
        return people.findClasses(majorId, cohortId).stream()
                .filter(c -> !c.isDeleted())
                .map(c -> AdministrativeClassResult.builder()
                        .id(c.getId())
                        .className(c.getClassName())
                        .majorCode(c.getMajorCode())
                        .majorName(c.getMajorName())
                        .cohortCode(c.getCohortCode())
                        .trainingType(c.getTrainingType())
                        .currentSize((int) c.getCurrentSize())
                        .minCapacity(c.getMinCapacity())
                        .maxCapacity(c.getMaxCapacity())
                        .reserveCapacity(c.getReserveCapacity())
                        .availableForAdmission(available(c))
                        .build())
                .toList();
    }

    @Transactional
    public PlanResult createFromPlan(CreateClassesCommand request) {
        AdmissionPlan plan = AdmissionPlanLoader.locked(plans, request.planId(), request.expectedRevision());
        AdmissionPlanPolicy.requireEditable(plan);
        if (applicants.findByPlan_IdOrderByRowNumber(plan.getId()).stream()
                .anyMatch(row -> row.getState() == AdmissionApplicantStatus.ENROLLED))
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
        if (request.classCount() < 1 || request.classCount() > 99 || plan.getTotalStudents() == 0)
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        var scenario = capacity
                .scenarios(
                        plan.getTotalStudents(),
                        plan.getMinCapacity(),
                        plan.getMaxCapacity(),
                        plan.getReserveCapacity(),
                        rooms.countSuitableRooms(plan.getMaxCapacity() + plan.getReserveCapacity()))
                .stream()
                .filter(s -> s.classCount() == request.classCount())
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_INVALID_CAPACITY));
        capacity.requireAcceptance(scenario, request.acceptReserve(), request.acceptBelowMinimum(), request.reason());
        people.lockCohort(plan.getCohortId());
        planClasses.deleteAll(planClasses.findByPlan_IdOrderByPosition(plan.getId()));
        planClasses.flush();
        List<AdmissionClassInfo> scope = people.findClasses(plan.getMajorId(), plan.getCohortId());
        List<AdmissionClassInfo> selected = new ArrayList<>();
        if (request.existingClassIds() != null) {
            if (new HashSet<>(request.existingClassIds()).size()
                            != request.existingClassIds().size()
                    || request.existingClassIds().size() > request.classCount())
                throw new BaseException(ErrorCode.VALIDATION_ERROR);
            for (Long id : request.existingClassIds()) {
                AdmissionClassInfo candidate = scope.stream()
                        .filter(c -> c.getId().equals(id))
                        .findFirst()
                        .orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE));
                if (!available(candidate)) throw new BaseException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE);
                var locked = people.lockClass(id);
                if (locked.getCurrentSize() != 0) throw new BaseException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE);
                selected.add(locked);
            }
        } else {
            for (var candidate : scope) {
                if (selected.size() == request.classCount()) break;
                if (available(candidate)) {
                    var locked = people.lockClass(candidate.getId());
                    if (locked.getCurrentSize() == 0) selected.add(locked);
                }
            }
        }
        // Tính cả lớp đã xóa/tên lớp cũ để không cấp lại số thứ tự đã sử dụng.
        int next = scope.stream()
                        .map(AdmissionClassInfo::getClassSequence)
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .max()
                        .orElse(0)
                + 1;
        while (selected.size() < request.classCount()) {
            if (next > 99) throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_CLASS);
            selected.add(people.createClass(AdmissionClassRequest.builder()
                    .majorId(plan.getMajorId())
                    .cohortId(plan.getCohortId())
                    .sequence(next++)
                    .minimum(plan.getMinCapacity())
                    .maximum(plan.getMaxCapacity())
                    .reserve(plan.getReserveCapacity())
                    .build()));
        }
        for (int i = 0; i < selected.size(); i++) {
            var target = people.configureClass(
                    selected.get(i).getId(), plan.getMinCapacity(), plan.getMaxCapacity(), plan.getReserveCapacity());
            planClasses.save(AdmissionPlanClass.builder()
                    .plan(plan)
                    .administrativeClassId(target.getId())
                    .position(i + 1)
                    .targetSize(scenario.targetSizes().get(i))
                    .build());
        }
        planClasses.flush();
        plan.setSelectedClassCount(request.classCount());
        plan.setExceptionReason(PersonText.optional(request.reason()));
        plan.setState(AdmissionPlanStatus.CLASSES_READY);
        applicants.findByPlan_IdOrderByRowNumber(plan.getId()).forEach(row -> row.setAdministrativeClassId(null));
        AdmissionPlanPolicy.changed(plan);
        // TODO(AUDIT_LOG): Ghi kịch bản được chọn, lớp tạo/dùng lại và xác nhận ngoại lệ.
        log.info("Prepared administrative classes: planId={}, classCount={}", plan.getId(), request.classCount());
        return view.response(plan);
    }

    private boolean available(AdmissionClassInfo value) {
        return !value.isDeleted()
                && value.getTrainingType() == TrainingType.CHINH_QUY
                && value.getClassSequence() != null
                && value.getLastStudentSequence() == 0
                && value.getCurrentSize() == 0
                && !planClasses.existsByAdministrativeClassId(value.getId());
    }
}
