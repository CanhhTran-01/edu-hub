package com.eduhub.edu_hub.people.application.admission;

import static com.eduhub.edu_hub.people.application.admission.AdmissionNamePolicy.MAX_SAME_NAME_PER_CLASS;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.people.api.dto.response.AdmissionResponses.*;
import com.eduhub.edu_hub.people.application.code.AdministrativeClassNames;
import com.eduhub.edu_hub.people.domain.model.*;
import com.eduhub.edu_hub.people.domain.repository.*;
import com.eduhub.edu_hub.scheduling.application.SchedulingFacade;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdmissionView {
    private final AdmissionApplicantRepository applicants;
    private final AdmissionPlanClassRepository planClasses;
    private final StudentRepository students;
    private final AdmissionCapacityPolicy capacity;
    private final SchedulingFacade.RoomCapacity rooms;
    private final CurriculumFacade.Majors majors;

    public PlanResponse response(AdmissionPlan plan) {
        List<AdmissionApplicant> rows = applicants.findByPlan_IdOrderByRowNumber(plan.getId());
        List<ClassPreview> classes = new ArrayList<>();
        List<BigDecimal> averages = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String prefix = AdministrativeClassNames.prefix(plan.getMajor().getMajorName());
        List<String> collisions = majors.findMajorsForClassNaming().stream()
                .filter(major -> !major.id().equals(plan.getMajor().getId()))
                .filter(major -> prefix.equals(AdministrativeClassNames.prefix(major.majorName())))
                .map(major -> major.majorName() + " (" + major.majorCode() + ")")
                .toList();
        if (!collisions.isEmpty()) {
            // TODO(CLASS_PREFIX_CONFIG): expose prefix overrides and an admin conflict-resolution UI.
            // This warning is informational: creating classes across different majors remains allowed.
            warnings.add(
                    "Tiền tố lớp " + prefix + " của ngành " + plan.getMajor().getMajorName()
                            + " trùng với: " + String.join(", ", collisions)
                            + ". Vẫn có thể tạo lớp; chức năng cấu hình tiền tố sẽ được bổ sung sau.");
        }
        for (AdmissionPlanClass slot : planClasses.findByPlan_IdOrderByPosition(plan.getId())) {
            AdministrativeClass target = slot.getAdministrativeClass();
            List<AdmissionApplicant> assigned = rows.stream()
                    .filter(AdmissionApplicant::isIncluded)
                    .filter(row -> row.getAdministrativeClass() != null
                            && target.getId()
                                    .equals(row.getAdministrativeClass().getId()))
                    .toList();
            BigDecimal average = null;
            if (!assigned.isEmpty()) {
                average = assigned.stream()
                        .map(AdmissionApplicant::getEntranceScore)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(assigned.size()), 12, RoundingMode.HALF_UP);
                averages.add(average);
            }
            Map<String, List<AdmissionApplicant>> names = assigned.stream()
                    .collect(Collectors.groupingBy(
                            row -> AdmissionText.key(row.getFullName()), LinkedHashMap::new, Collectors.toList()));
            int expectedSize =
                    plan.getState() == AdmissionPlan.State.DRAFT || plan.getState() == AdmissionPlan.State.CLASSES_READY
                            ? slot.getTargetSize()
                            : assigned.size();
            for (List<AdmissionApplicant> sameName : names.values()) {
                if (sameName.size() > MAX_SAME_NAME_PER_CLASS) {
                    warnings.add("Lớp " + target.getClassName() + " có " + sameName.size()
                            + " sinh viên trùng họ tên: " + sameName.getFirst().getFullName()
                            + " (tối đa " + MAX_SAME_NAME_PER_CLASS + ")");
                }
            }
            if (expectedSize < plan.getMinCapacity()) warnings.add("Lớp " + target.getClassName() + " dưới tối thiểu");
            if (expectedSize > plan.getMaxCapacity()) warnings.add("Lớp " + target.getClassName() + " dùng dự phòng");
            classes.add(new ClassPreview(
                    target.getId(),
                    target.getClassName(),
                    slot.getTargetSize(),
                    assigned.size(),
                    (int) students.countByAdministrativeClass_IdAndDeletedFalse(target.getId()),
                    plan.getMinCapacity(),
                    plan.getMaxCapacity(),
                    plan.getReserveCapacity(),
                    Math.max(0, expectedSize - plan.getMaxCapacity()),
                    average == null ? null : average.setScale(2, RoundingMode.HALF_UP),
                    assigned.stream().map(AdmissionApplicant::getId).toList()));
        }
        BigDecimal difference = averages.isEmpty()
                ? BigDecimal.ZERO
                : Collections.max(averages).subtract(Collections.min(averages)).setScale(2, RoundingMode.HALF_UP);
        long suitableRooms = rooms.countSuitableRooms(plan.getMaxCapacity() + plan.getReserveCapacity());
        List<Scenario> scenarios = capacity.scenarios(
                plan.getTotalStudents(),
                plan.getMinCapacity(),
                plan.getMaxCapacity(),
                plan.getReserveCapacity(),
                suitableRooms);
        if (plan.getTotalStudents() == 0) warnings.add("Không có hồ sơ hợp lệ mới cần phân bổ");
        if (plan.getTotalStudents() > 0 && scenarios.isEmpty()) {
            warnings.add("Không đủ phạm vi 99 lớp để cấp định danh cho số sinh viên này");
        }
        if (plan.getSelectedClassCount() != null && suitableRooms < plan.getSelectedClassCount()) {
            warnings.add("Số phòng đủ sức chứa M + R ít hơn số lớp đã chọn");
        }
        return new PlanResponse(
                plan.getId(),
                plan.getRevision(),
                plan.getMajor().getId(),
                plan.getCohort().getId(),
                plan.getTotalStudents(),
                new Capacity(plan.getMinCapacity(), plan.getMaxCapacity(), plan.getReserveCapacity()),
                plan.getState().name(),
                plan.getSelectedClassCount(),
                scenarios,
                List.copyOf(classes),
                rows.stream().map(this::applicant).toList(),
                difference,
                List.copyOf(warnings));
    }

    public ApplicantResponse applicant(AdmissionApplicant row) {
        Student student = row.getStudent();
        List<String> warnings = row.getWarningMessage() == null ? List.of() : List.of(row.getWarningMessage());
        return new ApplicantResponse(
                row.getId(),
                row.getRowNumber(),
                row.getApplicationCode(),
                row.getCitizenId(),
                row.getFullName(),
                row.getDateOfBirth(),
                row.getGender(),
                row.getEmail(),
                row.getPhone(),
                row.getEntranceScore(),
                row.getState().name(),
                row.getErrorMessage(),
                row.getAdministrativeClass() == null
                        ? null
                        : row.getAdministrativeClass().getId(),
                student == null ? null : student.getId(),
                student == null ? null : student.getStudentCode(),
                row.isReuseAccount(),
                warnings);
    }
}
