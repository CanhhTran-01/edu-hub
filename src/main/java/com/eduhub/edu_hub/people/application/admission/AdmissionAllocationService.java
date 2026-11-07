package com.eduhub.edu_hub.people.application.admission;

import com.eduhub.edu_hub.people.api.dto.request.AdmissionRequests.*;
import com.eduhub.edu_hub.people.api.dto.response.AdmissionResponses.PlanResponse;
import com.eduhub.edu_hub.people.domain.model.*;
import com.eduhub.edu_hub.people.domain.repository.*;
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
    private final AdministrativeClassRepository classes;
    private final StudentRepository students;
    private final AdmissionCapacityPolicy capacity;
    private final SerpentineClassAllocator allocator;
    private final AdmissionView view;

    @Transactional
    public PlanResponse preview(Long id, PreviewRequest request) {

        AdmissionPlan plan = AdmissionWorkflowSupport.locked(plans, id, request.expectedRevision()); // xử lý concurrent
        AdmissionWorkflowSupport.requireEditable(plan); // phải ở trạng thái còn cho phép sửa

        // chuẩn bị dữ liệu: Các “chỗ lớp” của kế hoạch, toàn bộ thí sinh, thí sinh đang chờ, thí sinh đã phân bổ
        List<AdmissionPlanClass> slots = planClasses.findByPlan_IdOrderByPosition(id);
        List<AdmissionApplicant> allRows = applicants.findByPlan_IdOrderByRowNumber(id);
        List<AdmissionApplicant> pendingRows = allRows.stream().filter(AdmissionApplicant::isPending).toList();
        List<AdmissionApplicant> enrolledRows = allRows.stream()
                .filter(row -> row.getState() == AdmissionApplicant.State.ENROLLED).toList();

        if (slots.isEmpty() || pendingRows.isEmpty() || pendingRows.size() + enrolledRows.size() != plan.getTotalStudents()) {
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

            var target = classes.findByIdForStudentCodeUpdate(slot.getAdministrativeClass().getId()).orElseThrow();

            long alreadyEnrolled = enrolledRows.stream()
                    .filter(row -> row.getAdministrativeClass().getId().equals(target.getId())).count();

            if (target.isDeleted() || target.getMaxCapacity() != plan.getMaxCapacity()
                    || target.getReserveCapacity() != plan.getReserveCapacity()
                    || students.countByAdministrativeClass_IdAndDeletedFalse(target.getId()) != alreadyEnrolled
                    || alreadyEnrolled > sizes.get(index)) {

                // dữ liệu đã đổi so với lúc cấu hình
                throw new BaseException(ErrorCode.ADMISSION_DATA_CHANGED);
            }

            slot.setTargetSize(sizes.get(index));

            AdmissionPlanClass remaining = new AdmissionPlanClass();
            remaining.setAdministrativeClass(target); // sĩ số mục tiêu đầy đủ
            remaining.setTargetSize(sizes.get(index) - (int) alreadyEnrolled); // // số chỗ CÒN LẠI để phân
            remainingSlots.add(remaining);
        }

        // Phân bổ và chuyển trạng thái
        allocator.assign(pendingRows, remainingSlots, enrolledRows);
        plan.setState(AdmissionPlan.State.PREVIEWED);

        AdmissionWorkflowSupport.changed(plan);
        log.info("Previewed admission assignment: planId={}, students={}, classes={}", id, pendingRows.size(), slots.size());

        return view.response(plan);
    }

    @Transactional
    public PlanResponse adjust(Long id, AdjustAssignmentsRequest request) {

        AdmissionPlan plan = AdmissionWorkflowSupport.locked(plans, id, request.expectedRevision());

        if (plan.getState() != AdmissionPlan.State.PREVIEWED
                && plan.getState() != AdmissionPlan.State.PARTIALLY_CONFIRMED) {
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
        }

        List<AdmissionPlanClass> slots = planClasses.findByPlan_IdOrderByPosition(id);
        List<AdmissionApplicant> rows = applicants.findByPlan_IdOrderByRowNumber(id);
        Set<Long> moved = new HashSet<>();

        for (Assignment move : request.assignments()) {
            // Chống chuyển trùng một người
            if (!moved.add(move.applicantId())) throw new BaseException(ErrorCode.VALIDATION_ERROR);

            // Tìm thí sinh đang chờ
            AdmissionApplicant row = rows.stream()
                    .filter(value -> value.getId().equals(move.applicantId()) && value.isPending()).findFirst()
                    .orElseThrow(() -> new BaseException(ErrorCode.VALIDATION_ERROR));

            // Tìm lớp đích, chỉ trong các lớp của plan
            AdministrativeClass target = slots.stream().map(AdmissionPlanClass::getAdministrativeClass)
                    .filter(value -> value.getId().equals(move.classId())).findFirst()
                    .orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE));

            // Gán lớp mới ***
            row.setAdministrativeClass(target);
        }

        // Kiểm tra sức chứa sau khi chuyển
        for (AdmissionPlanClass slot : slots) {
            // rows.stream() trên bộ nhớ Java, không query DB, nên không cần flush khi gán lớp mới (chỗ đánh ***)
            long projected = rows.stream().filter(AdmissionApplicant::isIncluded)
                    .filter(row -> row.getAdministrativeClass() != null && row.getAdministrativeClass().getId()
                                    .equals(slot.getAdministrativeClass().getId()))
                    .count();

            if (projected > plan.getMaxCapacity() + plan.getReserveCapacity()) {
                throw new BaseException(ErrorCode.CLASS_CAPACITY_EXCEEDED);
            }
        }

        AdmissionWorkflowSupport.changed(plan);

        // TODO(AUDIT_LOG): Record manual assignment changes with actor and previous class.
        log.info("Adjusted admission assignment: planId={}, moved={}", id, moved.size());

        return view.response(plan);
    }
}
