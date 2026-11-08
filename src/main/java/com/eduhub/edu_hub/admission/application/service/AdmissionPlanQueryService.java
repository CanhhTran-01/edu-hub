package com.eduhub.edu_hub.admission.application.service;

import static com.eduhub.edu_hub.admission.domain.policy.AdmissionDuplicateNamePolicy.MAX_SAME_NAME_PER_CLASS;

import com.eduhub.edu_hub.admission.application.mapper.AdmissionPlanAssembler;
import com.eduhub.edu_hub.admission.application.mapper.AdmissionScenarioMapper;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.ApplicantResult;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.ClassResult;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.PlanResult;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlanClass;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionCapacityPolicy;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionScoreBalancePolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionApplicantRepository;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanClassRepository;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.*;
import com.eduhub.edu_hub.scheduling.application.SchedulingFacade;
import com.eduhub.edu_hub.shared.exception.*;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
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
    private final AdmissionApplicantRepository applicantRepository;
    private final AdmissionPlanClassRepository planClassRepository;
    private final PeopleFacade.Admission peopleAdmissionFacade;
    private final AdmissionCapacityPolicy capacityPolicy;
    private final SchedulingFacade.RoomCapacity roomCapacityFacade;
    private final AdmissionScoreBalancePolicy scoreBalancePolicy;
    private final AdmissionPlanAssembler planAssembler;
    private final AdmissionScenarioMapper scenarioMapper;

    public PlanResult buildPlanResult(AdmissionPlan plan) {
        var planApplicants = applicantRepository.findByPlan_IdOrderByRowNumber(plan.getId());
        var plannedClasses = planClassRepository.findByPlan_IdOrderByPosition(plan.getId());
        var classesById = peopleAdmissionFacade
                .findClassesByIds(plannedClasses.stream()
                        .map(AdmissionPlanClass::getAdministrativeClassId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(AdmissionClassInfo::getId, Function.identity()));
        var studentsById = findStudentsById(planApplicants);
        List<ClassResult> classResults = new ArrayList<>();
        List<BigDecimal> classAverageScores = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        var classPrefixes = peopleAdmissionFacade.findClassPrefixes();
        var majorClassPrefix = classPrefixes.stream()
                .filter(classPrefix -> classPrefix.majorId().equals(plan.getMajorId()))
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_STUDENT_CODE_MAJOR));
        var majorsWithSamePrefix = classPrefixes.stream()
                .filter(classPrefix -> !classPrefix.majorId().equals(plan.getMajorId())
                        && classPrefix.prefix().equals(majorClassPrefix.prefix()))
                .map(classPrefix -> classPrefix.majorName() + " (" + classPrefix.majorCode() + ")")
                .toList();
        if (!majorsWithSamePrefix.isEmpty()) {
            // TODO(CLASS_PREFIX_CONFIG): Cho phép Admin cấu hình tiền tố; hiện trùng chỉ cảnh báo.
            warnings.add("Tiền tố lớp " + majorClassPrefix.prefix() + " của ngành " + majorClassPrefix.majorName()
                    + " trùng với: " + String.join(", ", majorsWithSamePrefix)
                    + ". Vẫn có thể tạo lớp; chức năng cấu hình tiền tố sẽ được bổ sung sau.");
        }
        for (var plannedClass : plannedClasses) {
            var administrativeClass = classesById.get(plannedClass.getAdministrativeClassId());
            if (administrativeClass == null) throw new BaseException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE);
            var assignedApplicants = planApplicants.stream()
                    .filter(AdmissionApplicant::isIncludedInPlan)
                    .filter(applicant -> administrativeClass.getId().equals(applicant.getAdministrativeClassId()))
                    .toList();
            BigDecimal classAverageScore = scoreBalancePolicy.calculateAverageScore(assignedApplicants.stream()
                    .map(AdmissionApplicant::getEntranceScore)
                    .toList());
            if (classAverageScore != null) classAverageScores.add(classAverageScore);
            var applicantsByNormalizedName = assignedApplicants.stream()
                    .collect(Collectors.groupingBy(
                            applicant -> TextsNormalizer.normalizeKey(applicant.getFullName()),
                            LinkedHashMap::new,
                            Collectors.toList()));
            for (var group : applicantsByNormalizedName.values())
                if (group.size() > MAX_SAME_NAME_PER_CLASS)
                    warnings.add("Lớp " + administrativeClass.getClassName() + " có " + group.size()
                            + " sinh viên trùng họ tên: " + group.getFirst().getFullName() + " (tối đa "
                            + MAX_SAME_NAME_PER_CLASS + ")");
            int projectedClassSize =
                    plan.getState() == AdmissionPlanStatus.DRAFT || plan.getState() == AdmissionPlanStatus.CLASSES_READY
                            ? plannedClass.getTargetSize()
                            : assignedApplicants.size();
            if (projectedClassSize < plan.getMinCapacity())
                warnings.add("Lớp " + administrativeClass.getClassName() + " dưới tối thiểu");
            if (projectedClassSize > plan.getMaxCapacity())
                warnings.add("Lớp " + administrativeClass.getClassName() + " dùng dự phòng");
            classResults.add(ClassResult.builder()
                    .id(administrativeClass.getId())
                    .className(administrativeClass.getClassName())
                    .targetSize(plannedClass.getTargetSize())
                    .assignedSize(assignedApplicants.size())
                    .currentSize((int) administrativeClass.getCurrentSize())
                    .minimum(plan.getMinCapacity())
                    .maximum(plan.getMaxCapacity())
                    .reserve(plan.getReserveCapacity())
                    .reserveUsed(Math.max(0, projectedClassSize - plan.getMaxCapacity()))
                    .averageScore(
                            classAverageScore == null ? null : classAverageScore.setScale(2, RoundingMode.HALF_UP))
                    .applicantIds(assignedApplicants.stream()
                            .map(AdmissionApplicant::getId)
                            .toList())
                    .build());
        }
        long suitableRoomCount =
                roomCapacityFacade.countSuitableRooms(plan.getMaxCapacity() + plan.getReserveCapacity());
        var scenarios = capacityPolicy.generateScenarios(
                plan.getTotalStudents(),
                plan.getMinCapacity(),
                plan.getMaxCapacity(),
                plan.getReserveCapacity(),
                suitableRoomCount);
        if (plan.getTotalStudents() == 0) warnings.add("Không có hồ sơ hợp lệ mới cần phân bổ");
        if (plan.getTotalStudents() > 0 && scenarios.isEmpty())
            warnings.add("Không đủ phạm vi 99 lớp để cấp định danh cho số sinh viên này");
        if (plan.getSelectedClassCount() != null && suitableRoomCount < plan.getSelectedClassCount())
            warnings.add("Số phòng đủ sức chứa M + R ít hơn số lớp đã chọn");
        return planAssembler.toPlanResult(
                plan,
                classResults,
                planApplicants.stream()
                        .map(applicant ->
                                planAssembler.toApplicantResult(applicant, studentsById.get(applicant.getStudentId())))
                        .toList(),
                scenarios.stream().map(scenarioMapper::toScenarioResult).toList(),
                scoreBalancePolicy.calculateAverageScoreGap(classAverageScores),
                warnings);
    }

    public List<ApplicantResult> buildApplicantResults(List<AdmissionApplicant> planApplicants) {
        var studentsById = findStudentsById(planApplicants);
        return planApplicants.stream()
                .map(applicant ->
                        planAssembler.toApplicantResult(applicant, studentsById.get(applicant.getStudentId())))
                .toList();
    }

    private Map<Long, AdmissionStudentInfo> findStudentsById(List<AdmissionApplicant> planApplicants) {
        var studentIds = planApplicants.stream()
                .map(AdmissionApplicant::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return peopleAdmissionFacade.findStudentsByIds(studentIds).stream()
                .collect(Collectors.toMap(AdmissionStudentInfo::getId, Function.identity()));
    }
}
