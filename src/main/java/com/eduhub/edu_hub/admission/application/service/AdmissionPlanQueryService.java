package com.eduhub.edu_hub.admission.application.service;

import static com.eduhub.edu_hub.admission.domain.policy.AdmissionNamePolicy.MAX_SAME_NAME_PER_CLASS;

import com.eduhub.edu_hub.admission.application.mapper.AdmissionPlanAssembler;
import com.eduhub.edu_hub.admission.application.mapper.AdmissionResultMapper;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.ApplicantResult;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.ClassResult;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.PlanResult;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlanClass;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionBalancePolicy;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionCapacityPolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionApplicantRepository;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanClassRepository;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.*;
import com.eduhub.edu_hub.scheduling.application.SchedulingFacade;
import com.eduhub.edu_hub.shared.exception.*;
import com.eduhub.edu_hub.shared.text.PersonText;
import java.math.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdmissionPlanQueryService {
    private final AdmissionApplicantRepository applicants;
    private final AdmissionPlanClassRepository planClasses;
    private final PeopleFacade.Admission people;
    private final AdmissionCapacityPolicy capacity;
    private final SchedulingFacade.RoomCapacity rooms;
    private final AdmissionBalancePolicy balance;
    private final AdmissionPlanAssembler assembler;
    private final AdmissionResultMapper resultMapper;

    public PlanResult response(AdmissionPlan plan) {
        var rows = applicants.findByPlan_IdOrderByRowNumber(plan.getId());
        var slots = planClasses.findByPlan_IdOrderByPosition(plan.getId());
        var classData = people
                .findClassesByIds(slots.stream()
                        .map(AdmissionPlanClass::getAdministrativeClassId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(AdmissionClassInfo::getId, Function.identity()));
        var studentData = students(rows);
        List<ClassResult> classes = new ArrayList<>();
        List<BigDecimal> averages = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        var prefixes = people.findClassPrefixes();
        var naming = prefixes.stream()
                .filter(p -> p.majorId().equals(plan.getMajorId()))
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_STUDENT_CODE_MAJOR));
        var collisions = prefixes.stream()
                .filter(p ->
                        !p.majorId().equals(plan.getMajorId()) && p.prefix().equals(naming.prefix()))
                .map(p -> p.majorName() + " (" + p.majorCode() + ")")
                .toList();
        if (!collisions.isEmpty()) {
            // TODO(CLASS_PREFIX_CONFIG): Cho phép Admin cấu hình tiền tố; hiện trùng chỉ cảnh báo.
            warnings.add("Tiền tố lớp " + naming.prefix() + " của ngành " + naming.majorName()
                    + " trùng với: " + String.join(", ", collisions)
                    + ". Vẫn có thể tạo lớp; chức năng cấu hình tiền tố sẽ được bổ sung sau.");
        }
        for (var slot : slots) {
            var target = classData.get(slot.getAdministrativeClassId());
            if (target == null) throw new BaseException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE);
            var assigned = rows.stream()
                    .filter(AdmissionApplicant::isIncluded)
                    .filter(r -> target.getId().equals(r.getAdministrativeClassId()))
                    .toList();
            BigDecimal average = balance.average(
                    assigned.stream().map(AdmissionApplicant::getEntranceScore).toList());
            if (average != null) averages.add(average);
            var names = assigned.stream()
                    .collect(Collectors.groupingBy(
                            r -> PersonText.key(r.getFullName()), LinkedHashMap::new, Collectors.toList()));
            for (var group : names.values())
                if (group.size() > MAX_SAME_NAME_PER_CLASS)
                    warnings.add("Lớp " + target.getClassName() + " có " + group.size() + " sinh viên trùng họ tên: "
                            + group.getFirst().getFullName() + " (tối đa " + MAX_SAME_NAME_PER_CLASS + ")");
            int expected =
                    plan.getState() == AdmissionPlanStatus.DRAFT || plan.getState() == AdmissionPlanStatus.CLASSES_READY
                            ? slot.getTargetSize()
                            : assigned.size();
            if (expected < plan.getMinCapacity()) warnings.add("Lớp " + target.getClassName() + " dưới tối thiểu");
            if (expected > plan.getMaxCapacity()) warnings.add("Lớp " + target.getClassName() + " dùng dự phòng");
            classes.add(ClassResult.builder()
                    .id(target.getId())
                    .className(target.getClassName())
                    .targetSize(slot.getTargetSize())
                    .assignedSize(assigned.size())
                    .currentSize((int) target.getCurrentSize())
                    .minimum(plan.getMinCapacity())
                    .maximum(plan.getMaxCapacity())
                    .reserve(plan.getReserveCapacity())
                    .reserveUsed(Math.max(0, expected - plan.getMaxCapacity()))
                    .averageScore(average == null ? null : average.setScale(2, RoundingMode.HALF_UP))
                    .applicantIds(
                            assigned.stream().map(AdmissionApplicant::getId).toList())
                    .build());
        }
        long suitable = rooms.countSuitableRooms(plan.getMaxCapacity() + plan.getReserveCapacity());
        var scenarios = capacity.scenarios(
                plan.getTotalStudents(),
                plan.getMinCapacity(),
                plan.getMaxCapacity(),
                plan.getReserveCapacity(),
                suitable);
        if (plan.getTotalStudents() == 0) warnings.add("Không có hồ sơ hợp lệ mới cần phân bổ");
        if (plan.getTotalStudents() > 0 && scenarios.isEmpty())
            warnings.add("Không đủ phạm vi 99 lớp để cấp định danh cho số sinh viên này");
        if (plan.getSelectedClassCount() != null && suitable < plan.getSelectedClassCount())
            warnings.add("Số phòng đủ sức chứa M + R ít hơn số lớp đã chọn");
        return assembler.plan(
                plan,
                classes,
                rows.stream()
                        .map(r -> assembler.applicant(r, studentData.get(r.getStudentId())))
                        .toList(),
                scenarios.stream().map(resultMapper::scenario).toList(),
                balance.difference(averages),
                warnings);
    }

    public List<ApplicantResult> applicantResults(List<AdmissionApplicant> rows) {
        var students = students(rows);
        return rows.stream()
                .map(r -> assembler.applicant(r, students.get(r.getStudentId())))
                .toList();
    }

    private Map<Long, AdmissionStudentInfo> students(List<AdmissionApplicant> rows) {
        var ids = rows.stream()
                .map(AdmissionApplicant::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return people.findStudentsByIds(ids).stream()
                .collect(Collectors.toMap(AdmissionStudentInfo::getId, Function.identity()));
    }
}
