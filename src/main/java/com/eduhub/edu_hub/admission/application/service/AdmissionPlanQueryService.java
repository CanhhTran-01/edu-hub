package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.api.dto.response.AdmissionApplicantResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionClassPreviewResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanResponse;
import com.eduhub.edu_hub.admission.api.mapper.AdmissionPlanResponseMapper;
import com.eduhub.edu_hub.admission.api.mapper.AdmissionScenarioResponseMapper;
import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlanClass;
import com.eduhub.edu_hub.admission.domain.model.ClassDivisionScenario;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionCapacityPolicy;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionPlanPolicy;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionScoreBalancePolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionApplicantRepository;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanClassRepository;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.*;
import com.eduhub.edu_hub.scheduling.application.facade.SchedulingFacade;
import com.eduhub.edu_hub.shared.exception.*;
import com.eduhub.edu_hub.shared.systemconfig.application.service.SystemConfigService;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
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
    private final AdmissionPlanResponseMapper planResponseMapper;
    private final AdmissionScenarioResponseMapper scenarioResponseMapper;
    private final SystemConfigService systemConfigService;

    public AdmissionPlanResponse buildPlanResponse(AdmissionPlan plan) {
        return buildPlanResponse(
                plan, systemConfigService.getSnapshot().getInteger(SystemConfigKey.ADMISSION_MAXIMUM_SAME_NAME));
    }

    public AdmissionPlanResponse buildPlanResponse(AdmissionPlan plan, int maximumSameName) {
        // Tải dữ liệu dùng chung trước khi dựng hồ sơ, thống kê lớp và cảnh báo.
        var planApplicants = applicantRepository.findByPlan_IdAndDeletedFalseOrderByRowNumber(plan.getId());
        var plannedClasses = planClassRepository.findByPlan_IdOrderByPosition(plan.getId());
        var classesById = findClassesById(plannedClasses);
        var studentsById = findStudentsById(planApplicants);

        // Giữ thứ tự cảnh báo: tiền tố lớp, từng lớp, rồi các giới hạn của toàn phương án.
        List<String> warnings = new ArrayList<>();
        appendClassPrefixWarnings(plan.getMajorId(), warnings);
        var classPreviews =
                buildClassPreviewSummary(plan, planApplicants, plannedClasses, classesById, maximumSameName);
        warnings.addAll(classPreviews.warnings());

        long suitableRoomCount =
                roomCapacityFacade.countSuitableRooms(plan.getMaxCapacity() + plan.getReserveCapacity());
        var scenarios = buildPlanScenarios(plan, planApplicants, classPreviews.classResponses(), suitableRoomCount);
        appendPlanWarnings(plan, scenarios, suitableRoomCount, warnings);

        return planResponseMapper.toPlanResponse(
                plan,
                classPreviews.classResponses(),
                buildApplicantResponses(planApplicants, studentsById),
                scenarios.stream()
                        .map(scenarioResponseMapper::toScenarioResponse)
                        .toList(),
                scoreBalancePolicy.calculateAverageScoreGap(classPreviews.classAverageScores()),
                warnings,
                AdmissionPlanPolicy.findConfirmationBlockReasons(plan, planApplicants, plannedClasses));
    }

    public List<AdmissionApplicantResponse> buildApplicantResponses(List<AdmissionApplicant> planApplicants) {
        return buildApplicantResponses(planApplicants, findStudentsById(planApplicants));
    }

    private Map<Long, AdmissionClassInfo> findClassesById(List<AdmissionPlanClass> plannedClasses) {
        return peopleAdmissionFacade
                .findClassesByIds(plannedClasses.stream()
                        .map(AdmissionPlanClass::getAdministrativeClassId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(AdmissionClassInfo::getId, Function.identity()));
    }

    private void appendClassPrefixWarnings(Long majorId, List<String> warnings) {
        var classPrefixes = peopleAdmissionFacade.findClassPrefixes();
        var majorClassPrefix = classPrefixes.stream()
                .filter(classPrefix -> classPrefix.majorId().equals(majorId))
                .findFirst()
                .orElseThrow(() -> new AdmissionPlanException(ErrorCode.INVALID_STUDENT_CODE_MAJOR));
        var majorsWithSamePrefix = classPrefixes.stream()
                .filter(classPrefix -> !classPrefix.majorId().equals(majorId)
                        && classPrefix.prefix().equals(majorClassPrefix.prefix()))
                .map(classPrefix -> classPrefix.majorName() + " (" + classPrefix.majorCode() + ")")
                .toList();
        if (!majorsWithSamePrefix.isEmpty()) {
            // TODO(CLASS_PREFIX_CONFIG): Cho phép Admin cấu hình tiền tố; hiện trùng chỉ cảnh báo.
            warnings.add("Tiền tố lớp " + majorClassPrefix.prefix() + " của ngành " + majorClassPrefix.majorName()
                    + " trùng với: " + String.join(", ", majorsWithSamePrefix)
                    + ". Vẫn có thể tạo lớp; chức năng cấu hình tiền tố sẽ được bổ sung sau.");
        }
    }

    private ClassPreviewSummary buildClassPreviewSummary(
            AdmissionPlan plan,
            List<AdmissionApplicant> planApplicants,
            List<AdmissionPlanClass> plannedClasses,
            Map<Long, AdmissionClassInfo> classesById,
            int maximumSameName) {
        List<AdmissionClassPreviewResponse> classResponses = new ArrayList<>();
        List<BigDecimal> classAverageScores = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (var plannedClass : plannedClasses) {
            var administrativeClass = classesById.get(plannedClass.getAdministrativeClassId());
            if (administrativeClass == null) throw new AdmissionPlanException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE);
            var assignedApplicants = planApplicants.stream()
                    .filter(AdmissionApplicant::isIncludedInPlan)
                    .filter(applicant -> administrativeClass.getId().equals(applicant.getAdministrativeClassId()))
                    .toList();
            BigDecimal classAverageScore = scoreBalancePolicy.calculateAverageScore(assignedApplicants.stream()
                    .map(AdmissionApplicant::getEntranceScore)
                    .toList());
            if (classAverageScore != null) classAverageScores.add(classAverageScore);
            appendDuplicateNameWarnings(administrativeClass, assignedApplicants, maximumSameName, warnings);
            int projectedClassSize =
                    plan.getState() == AdmissionPlanStatus.DRAFT || plan.getState() == AdmissionPlanStatus.CLASSES_READY
                            ? plannedClass.getTargetSize()
                            : assignedApplicants.size();
            if (projectedClassSize < plan.getMinCapacity())
                warnings.add("Lớp " + administrativeClass.getClassName() + " dưới tối thiểu");
            if (projectedClassSize > plan.getMaxCapacity())
                warnings.add("Lớp " + administrativeClass.getClassName() + " dùng dự phòng");
            classResponses.add(AdmissionClassPreviewResponse.builder()
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
        return new ClassPreviewSummary(classResponses, classAverageScores, warnings);
    }

    private void appendDuplicateNameWarnings(
            AdmissionClassInfo administrativeClass,
            List<AdmissionApplicant> assignedApplicants,
            int maximumSameName,
            List<String> warnings) {
        var applicantsByNormalizedName = assignedApplicants.stream()
                .collect(Collectors.groupingBy(
                        applicant -> TextsNormalizer.normalizeKey(applicant.getFullName()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        for (var group : applicantsByNormalizedName.values())
            if (group.size() > maximumSameName)
                warnings.add("Lớp " + administrativeClass.getClassName() + " có " + group.size()
                        + " sinh viên trùng họ tên: " + group.getFirst().getFullName() + " (tối đa "
                        + maximumSameName + ")");
    }

    private List<ClassDivisionScenario> buildPlanScenarios(
            AdmissionPlan plan,
            List<AdmissionApplicant> planApplicants,
            List<AdmissionClassPreviewResponse> classResponses,
            long suitableRoomCount) {
        var scenarios = capacityPolicy.generateScenarios(
                plan.getTotalStudents(),
                plan.getMinCapacity(),
                plan.getMaxCapacity(),
                plan.getReserveCapacity(),
                suitableRoomCount);
        if (planApplicants.stream().anyMatch(applicant -> applicant.getState() == AdmissionApplicantStatus.ENROLLED)
                && !classResponses.isEmpty()) {
            // Sau ENROLLED, chia đều lại theo tổng có thể tạo kịch bản không thực hiện được nếu phải chuyển người cũ.
            var actualSizes = classResponses.stream()
                    .map(classResponse -> plan.getState() == AdmissionPlanStatus.DRAFT
                                    || plan.getState() == AdmissionPlanStatus.CLASSES_READY
                            ? classResponse.targetSize()
                            : classResponse.assignedSize())
                    .toList();
            // DRAFT có thể còn chỉ tiêu lớp cũ sau lần bổ sung vượt giới hạn; không coi đó là kịch bản đủ chỗ.
            scenarios = actualSizes.stream().mapToInt(Integer::intValue).sum() == plan.getTotalStudents()
                    ? List.of(capacityPolicy.describeClassSizes(
                            actualSizes,
                            plan.getMinCapacity(),
                            plan.getMaxCapacity(),
                            plan.getReserveCapacity(),
                            suitableRoomCount,
                            true))
                    : List.of();
        }
        return scenarios;
    }

    private void appendPlanWarnings(
            AdmissionPlan plan, List<ClassDivisionScenario> scenarios, long suitableRoomCount, List<String> warnings) {
        if (plan.getTotalStudents() == 0) warnings.add("Không có hồ sơ hợp lệ mới cần phân bổ");
        if (plan.getTotalStudents() > 0 && scenarios.isEmpty())
            warnings.add("Không đủ phạm vi 99 lớp để cấp định danh cho số sinh viên này");
        if (plan.getSelectedClassCount() != null && suitableRoomCount < plan.getSelectedClassCount())
            warnings.add("Số phòng đủ sức chứa M + R ít hơn số lớp đã chọn");
    }

    private List<AdmissionApplicantResponse> buildApplicantResponses(
            List<AdmissionApplicant> planApplicants, Map<Long, AdmissionStudentInfo> studentsById) {
        return planApplicants.stream()
                .map(applicant ->
                        planResponseMapper.toApplicantResponse(applicant, studentsById.get(applicant.getStudentId())))
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

    // Giữ điểm trung bình chưa làm tròn để tính chênh lệch; giá trị trong DTO chỉ phục vụ hiển thị.
    private record ClassPreviewSummary(
            List<AdmissionClassPreviewResponse> classResponses,
            List<BigDecimal> classAverageScores,
            List<String> warnings) {}
}
