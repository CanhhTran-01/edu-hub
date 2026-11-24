package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.model.*;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionCapacityPolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanClassRepository;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.*;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Chuẩn bị lớp tự động trong transaction upload/sửa hồ sơ; không nhận lựa chọn sĩ số hoặc lớp từ FE. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AdmissionClassPreparationService {
    private final AdmissionPlanClassRepository planClassRepository;
    private final PeopleFacade.Admission peopleAdmissionFacade;
    private final AdmissionCapacityPolicy capacityPolicy;

    public List<AdmissionPlanClass> prepareClassesAutomatically(
            AdmissionPlan plan, List<AdmissionApplicant> applicants) {
        var plannedClasses = planClassRepository.findByPlan_IdOrderByPosition(plan.getId());
        Map<Long, Long> enrolledCountsByClassId = applicants.stream()
                .filter(applicant -> applicant.getState() == AdmissionApplicantStatus.ENROLLED)
                .collect(Collectors.groupingBy(AdmissionApplicant::getAdministrativeClassId, Collectors.counting()));
        var occupiedClasses = plannedClasses.stream()
                .filter(plannedClass -> enrolledCountsByClassId.containsKey(plannedClass.getAdministrativeClassId()))
                .toList();
        if (occupiedClasses.size() != enrolledCountsByClassId.size()) {
            throw new AdmissionPlanException(ErrorCode.ADMISSION_DATA_CHANGED);
        }
        List<Integer> targetSizes = capacityPolicy.selectAutomaticClassSizes(
                plan.getTotalStudents(),
                occupiedClasses.stream()
                        .map(plannedClass -> enrolledCountsByClassId
                                .get(plannedClass.getAdministrativeClassId())
                                .intValue())
                        .toList(),
                plan.getMinCapacity(),
                plan.getMaxCapacity(),
                plan.getReserveCapacity());
        if (targetSizes.isEmpty()) return List.of();

        // Khóa khóa học để tuần tự hóa cấp tên lớp giữa các plan và các lần upload cùng phạm vi.
        peopleAdmissionFacade.lockCohort(plan.getCohortId());
        var classesInScope = peopleAdmissionFacade.findClasses(plan.getMajorId(), plan.getCohortId());
        // Giữ nguyên lớp đã có người nhập học; chỉ điều chỉnh phần liên kết của các lớp trống.
        var selectedClasses = retainRequiredPlanClasses(
                plan, plannedClasses, occupiedClasses, enrolledCountsByClassId, targetSizes.size());
        int nextPosition = plannedClasses.stream()
                        .mapToInt(AdmissionPlanClass::getPosition)
                        .max()
                        .orElse(0)
                + 1;

        int nextClassSequence = calculateNextClassSequence(classesInScope);
        nextPosition = reuseAvailableClasses(plan, classesInScope, selectedClasses, targetSizes.size(), nextPosition);
        createMissingClasses(plan, selectedClasses, targetSizes.size(), nextPosition, nextClassSequence);
        configureSelectedClasses(plan, selectedClasses, enrolledCountsByClassId, targetSizes);
        planClassRepository.saveAll(selectedClasses);
        planClassRepository.flush();
        plan.setSelectedClassCount(selectedClasses.size());
        // TODO(AUDIT_LOG): Ghi kịch bản tự chọn và lớp tạo/dùng lại sau commit; ngoại lệ được chấp nhận lúc xác nhận.
        return selectedClasses;
    }

    private boolean isAvailableForAdmission(AdmissionClassInfo administrativeClass) {
        return !administrativeClass.isDeleted()
                && administrativeClass.getTrainingType() == TrainingType.CHINH_QUY
                && administrativeClass.getClassSequence() != null
                && administrativeClass.getLastStudentSequence() == 0
                && administrativeClass.getCurrentSize() == 0
                && !planClassRepository.existsByAdministrativeClassId(administrativeClass.getId());
    }

    private List<AdmissionPlanClass> retainRequiredPlanClasses(
            AdmissionPlan plan,
            List<AdmissionPlanClass> plannedClasses,
            List<AdmissionPlanClass> occupiedClasses,
            Map<Long, Long> enrolledCountsByClassId,
            int targetClassCount) {
        List<AdmissionPlanClass> selectedClasses = new ArrayList<>(occupiedClasses);
        for (var plannedClass : plannedClasses) {
            AdmissionClassInfo lockedClass = peopleAdmissionFacade.lockClass(plannedClass.getAdministrativeClassId());
            long enrolledCount = enrolledCountsByClassId.getOrDefault(plannedClass.getAdministrativeClassId(), 0L);
            if (lockedClass.isDeleted()
                    || lockedClass.getCurrentSize() != enrolledCount
                    || !Objects.equals(lockedClass.getMajorId(), plan.getMajorId())
                    || !Objects.equals(lockedClass.getCohortId(), plan.getCohortId())
                    || lockedClass.getMaxCapacity() != plan.getMaxCapacity()
                    || lockedClass.getReserveCapacity() != plan.getReserveCapacity()) {
                throw new AdmissionPlanException(ErrorCode.ADMISSION_DATA_CHANGED);
            }
            if (enrolledCount == 0 && selectedClasses.size() < targetClassCount) selectedClasses.add(plannedClass);
        }
        // Chỉ bỏ liên kết các lớp trống không còn cần; giữ lớp thật và bộ đếm định danh trong People.
        planClassRepository.deleteAll(plannedClasses.stream()
                .filter(plannedClass -> !selectedClasses.contains(plannedClass))
                .toList());
        planClassRepository.flush();
        return selectedClasses;
    }

    private int calculateNextClassSequence(List<AdmissionClassInfo> classesInScope) {
        // Tính cả lớp đã xóa/tên lớp cũ để không cấp lại số thứ tự đã sử dụng.
        int nextClassSequence = classesInScope.stream()
                        .map(AdmissionClassInfo::getClassSequence)
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .max()
                        .orElse(0)
                + 1;
        return nextClassSequence;
    }

    private int reuseAvailableClasses(
            AdmissionPlan plan,
            List<AdmissionClassInfo> classesInScope,
            List<AdmissionPlanClass> selectedClasses,
            int targetClassCount,
            int nextPosition) {
        for (AdmissionClassInfo candidateClass : classesInScope) {
            if (selectedClasses.size() == targetClassCount) break;
            if (!isAvailableForAdmission(candidateClass)) continue;
            var lockedClass = peopleAdmissionFacade.lockClass(candidateClass.getId());
            if (lockedClass.getCurrentSize() == 0
                    && lockedClass.getLastStudentSequence() == 0
                    && !lockedClass.isDeleted()) {
                selectedClasses.add(AdmissionPlanClass.builder()
                        .plan(plan)
                        .administrativeClassId(lockedClass.getId())
                        .position(nextPosition++)
                        .build());
            }
        }
        return nextPosition;
    }

    private void createMissingClasses(
            AdmissionPlan plan,
            List<AdmissionPlanClass> selectedClasses,
            int targetClassCount,
            int nextPosition,
            int nextClassSequence) {
        while (selectedClasses.size() < targetClassCount) {
            if (nextClassSequence > 99) throw new AdmissionPlanException(ErrorCode.INVALID_STUDENT_CODE_CLASS);
            var createdClass = peopleAdmissionFacade.createClass(AdmissionClassRequest.builder()
                    .majorId(plan.getMajorId())
                    .cohortId(plan.getCohortId())
                    .sequence(nextClassSequence++)
                    .minimum(plan.getMinCapacity())
                    .maximum(plan.getMaxCapacity())
                    .reserve(plan.getReserveCapacity())
                    .build());
            selectedClasses.add(AdmissionPlanClass.builder()
                    .plan(plan)
                    .administrativeClassId(createdClass.getId())
                    .position(nextPosition++)
                    .build());
        }
    }

    private void configureSelectedClasses(
            AdmissionPlan plan,
            List<AdmissionPlanClass> selectedClasses,
            Map<Long, Long> enrolledCountsByClassId,
            List<Integer> targetSizes) {
        for (int classIndex = 0; classIndex < selectedClasses.size(); classIndex++) {
            var plannedClass = selectedClasses.get(classIndex);
            if (!enrolledCountsByClassId.containsKey(plannedClass.getAdministrativeClassId())) {
                peopleAdmissionFacade.configureClass(
                        plannedClass.getAdministrativeClassId(),
                        plan.getMinCapacity(),
                        plan.getMaxCapacity(),
                        plan.getReserveCapacity());
            }
            plannedClass.setTargetSize(targetSizes.get(classIndex));
        }
    }
}
