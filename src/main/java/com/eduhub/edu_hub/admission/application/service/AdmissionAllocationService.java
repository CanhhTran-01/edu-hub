package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.AdjustAssignmentsCommand;
import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.AssignmentCommand;
import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.PreviewCommand;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.PlanResult;
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
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanRepository;
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
    private final AdmissionPlanRepository plans;
    private final AdmissionPlanClassRepository planClasses;
    private final AdmissionApplicantRepository applicants;

    private final PeopleFacade.Admission people;
    private final AdmissionCapacityPolicy capacity;
    private final SerpentineClassAllocator allocator;
    private final AdmissionPlanQueryService view;

    @Transactional
    public PlanResult preview(Long id, PreviewCommand request) {

        AdmissionPlan plan = AdmissionPlanLoader.locked(plans, id, request.expectedRevision()); // xử lý concurrent
        AdmissionPlanPolicy.requireEditable(plan); // phải ở trạng thái còn cho phép sửa

        // chuẩn bị dữ liệu: Các “chỗ lớp” của kế hoạch, toàn bộ thí sinh, thí sinh đang chờ, thí sinh đã phân bổ
        List<AdmissionPlanClass> slots = planClasses.findByPlan_IdOrderByPosition(id);
        List<AdmissionApplicant> allRows = applicants.findByPlan_IdOrderByRowNumber(id);
        List<AdmissionApplicant> pendingRows =
                allRows.stream().filter(AdmissionApplicant::isPending).toList();
        List<AdmissionApplicant> enrolledRows = allRows.stream()
                .filter(row -> row.getState() == AdmissionApplicantStatus.ENROLLED)
                .toList();

        if (slots.isEmpty()
                || pendingRows.isEmpty()
                || pendingRows.size() + enrolledRows.size() != plan.getTotalStudents()) {
            // dữ liệu lệch so với lúc import
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
        }

        // Tính sĩ số mục tiêu mỗi lớp
        List<Integer> sizes = capacity.sizes(plan.getTotalStudents(), slots.size());
        if (sizes.getFirst() > plan.getMaxCapacity() + plan.getReserveCapacity()) {
            throw new BaseException(ErrorCode.ADMISSION_INVALID_CAPACITY);
        }

        List<AdmissionPlanClass> remainingSlots = new ArrayList<>();

        for (int index = 0; index < slots.size(); index++) {

            var slot = slots.get(index);

            var target = people.lockClass(slot.getAdministrativeClassId());

            long alreadyEnrolled = enrolledRows.stream()
                    .filter(row -> row.getAdministrativeClassId().equals(target.getId()))
                    .count();

            if (target.isDeleted()
                    || target.getMaxCapacity() != plan.getMaxCapacity()
                    || target.getReserveCapacity() != plan.getReserveCapacity()
                    || target.getCurrentSize() != alreadyEnrolled
                    || alreadyEnrolled > sizes.get(index)) {

                // dữ liệu đã đổi so với lúc cấu hình
                throw new BaseException(ErrorCode.ADMISSION_DATA_CHANGED);
            }

            slot.setTargetSize(sizes.get(index));

            // Chỉ phân vào số chỗ còn lại; không chuyển lớp những người đã nhập học thành công.
            AdmissionPlanClass remaining = AdmissionPlanClass.builder()
                    .administrativeClassId(target.getId())
                    .targetSize(sizes.get(index) - (int) alreadyEnrolled)
                    .build();
            remainingSlots.add(remaining);
        }

        // Phân bổ và chuyển trạng thái
        allocator.assign(pendingRows, remainingSlots, enrolledRows);
        plan.setState(AdmissionPlanStatus.PREVIEWED);

        AdmissionPlanPolicy.changed(plan);
        log.info(
                "Previewed admission assignment: planId={}, students={}, classes={}",
                id,
                pendingRows.size(),
                slots.size());

        return view.response(plan);
    }

    @Transactional
    public PlanResult adjust(Long id, AdjustAssignmentsCommand request) {

        AdmissionPlan plan = AdmissionPlanLoader.locked(plans, id, request.expectedRevision());

        if (plan.getState() != AdmissionPlanStatus.PREVIEWED
                && plan.getState() != AdmissionPlanStatus.PARTIALLY_CONFIRMED) {
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
        }

        List<AdmissionPlanClass> slots = planClasses.findByPlan_IdOrderByPosition(id);
        List<AdmissionApplicant> rows = applicants.findByPlan_IdOrderByRowNumber(id);
        Set<Long> moved = new HashSet<>();

        for (AssignmentCommand move : request.assignments()) {
            // Chống chuyển trùng một người
            if (!moved.add(move.applicantId())) throw new BaseException(ErrorCode.VALIDATION_ERROR);

            // Tìm thí sinh đang chờ
            AdmissionApplicant row = rows.stream()
                    .filter(value -> value.getId().equals(move.applicantId()) && value.isPending())
                    .findFirst()
                    .orElseThrow(() -> new BaseException(ErrorCode.VALIDATION_ERROR));

            // Tìm lớp đích, chỉ trong các lớp của plan
            Long target = slots.stream()
                    .map(AdmissionPlanClass::getAdministrativeClassId)
                    .filter(value -> value.equals(move.classId()))
                    .findFirst()
                    .orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE));

            // Gán lớp mới ***
            row.setAdministrativeClassId(target);
        }

        // Kiểm tra sức chứa sau khi chuyển
        for (AdmissionPlanClass slot : slots) {
            // rows.stream() trên bộ nhớ Java, không query DB, nên không cần flush khi gán lớp mới (chỗ đánh ***)
            long projected = rows.stream()
                    .filter(AdmissionApplicant::isIncluded)
                    .filter(row -> row.getAdministrativeClassId() != null
                            && row.getAdministrativeClassId().equals(slot.getAdministrativeClassId()))
                    .count();

            if (projected > plan.getMaxCapacity() + plan.getReserveCapacity()) {
                throw new BaseException(ErrorCode.CLASS_CAPACITY_EXCEEDED);
            }
        }

        AdmissionPlanPolicy.changed(plan);

        // TODO(AUDIT_LOG): Ghi người điều chỉnh, lớp trước/sau của từng hồ sơ khi transaction đã commit.
        log.info("Adjusted admission assignment: planId={}, moved={}", id, moved.size());

        return view.response(plan);
    }
}
