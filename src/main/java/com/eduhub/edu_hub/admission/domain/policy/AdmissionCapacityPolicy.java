package com.eduhub.edu_hub.admission.domain.policy;

import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.domain.model.ClassDivisionScenario;
import com.eduhub.edu_hub.shared.common.TextsNormalizer;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AdmissionCapacityPolicy {

    public void validateCapacityLimits(int minCapacity, int maxCapacity, int reserveCapacity) {
        if (minCapacity < 1
                || maxCapacity < minCapacity
                || reserveCapacity < 0
                || maxCapacity > 99
                || reserveCapacity > 99 - maxCapacity) {
            throw new AdmissionPlanException(ErrorCode.ADMISSION_INVALID_CAPACITY);
        }
    }

    public List<ClassDivisionScenario> generateScenarios(
            int totalStudents, int minCapacity, int maxCapacity, int reserveCapacity, long suitableRoomCount) {

        validateCapacityLimits(minCapacity, maxCapacity, reserveCapacity);
        if (totalStudents < 0) throw new AdmissionPlanException(ErrorCode.VALIDATION_ERROR);

        if (totalStudents == 0) return List.of();
        if (divideRoundUp(totalStudents, maxCapacity + reserveCapacity) > 99) return List.of();

        List<ClassDivisionScenario> scenarios = new ArrayList<>();
        int maxClassesAtMinimumCapacity = Math.min(99, totalStudents / minCapacity);

        // Case 1: Không cần suất dự phòng, ví dụ 140 / 40 = 3.5 --> 4 lớp tối thiểu
        int minClassesWithoutReserve = divideRoundUp(totalStudents, maxCapacity);
        if (minClassesWithoutReserve <= maxClassesAtMinimumCapacity) {
            scenarios.addAll(buildClassCountScenarios(
                    totalStudents,
                    minClassesWithoutReserve,
                    maxClassesAtMinimumCapacity,
                    minCapacity,
                    maxCapacity,
                    reserveCapacity,
                    suitableRoomCount));
            return List.copyOf(scenarios);
        }

        return buildReserveScenarios(
                totalStudents,
                minClassesWithoutReserve,
                maxClassesAtMinimumCapacity,
                minCapacity,
                maxCapacity,
                reserveCapacity,
                suitableRoomCount);
    }

    public ClassDivisionScenario buildScenario(
            int totalStudents,
            int classCount,
            int minCapacity,
            int maxCapacity,
            int reserveCapacity,
            long suitableRoomCount,
            boolean recommended) {

        validateCapacityLimits(minCapacity, maxCapacity, reserveCapacity);
        if (totalStudents < 0 || classCount < 1) throw new AdmissionPlanException(ErrorCode.VALIDATION_ERROR);

        // phân phối đều total thành count phần bằng nhau nhất, danh sách theo thứ tự giảm dần
        return describeClassSizes(
                distributeEvenly(totalStudents, classCount),
                minCapacity,
                maxCapacity,
                reserveCapacity,
                suitableRoomCount,
                recommended);
    }

    /** Mô tả đúng sĩ số của phương án đã phân, kể cả khi phải giữ các lớp ENROLLED không đều nhau. */
    public ClassDivisionScenario describeClassSizes(
            List<Integer> targetClassSizes,
            int minCapacity,
            int maxCapacity,
            int reserveCapacity,
            long suitableRoomCount,
            boolean recommended) {
        validateCapacityLimits(minCapacity, maxCapacity, reserveCapacity);
        if (targetClassSizes.isEmpty() || targetClassSizes.stream().anyMatch(size -> size < 0)) {
            throw new AdmissionPlanException(ErrorCode.VALIDATION_ERROR);
        }
        int classCount = targetClassSizes.size();
        if (targetClassSizes.stream().anyMatch(size -> size > maxCapacity + reserveCapacity))
            throw new AdmissionPlanException(ErrorCode.CLASS_CAPACITY_EXCEEDED);

        List<String> warnings = new ArrayList<>();
        boolean hasUnderfilledClass = targetClassSizes.stream().anyMatch(size -> size < minCapacity);
        int reserveSeatsUsed = targetClassSizes.stream()
                .mapToInt(size -> Math.max(0, size - maxCapacity))
                .sum();

        if (reserveSeatsUsed > 0) warnings.add("Phương án sử dụng " + reserveSeatsUsed + " suất dự phòng");
        if (hasUnderfilledClass) warnings.add("Có lớp có sĩ số dưới mức tối thiểu");
        if (suitableRoomCount < classCount)
            warnings.add("Thiếu phòng có sức chứa từ " + (maxCapacity + reserveCapacity) + " chỗ");
        if (classCount > 99) warnings.add("Số lớp vượt phạm vi định danh hai chữ số");

        return ClassDivisionScenario.builder()
                .classCount(classCount)
                .targetSizes(targetClassSizes)
                .level(hasUnderfilledClass ? "BELOW_MINIMUM" : reserveSeatsUsed > 0 ? "RESERVE" : "NORMAL")
                .reserveUsed(reserveSeatsUsed)
                .belowMinimum(hasUnderfilledClass)
                .insufficientRooms(suitableRoomCount < classCount)
                .suitableRooms(suitableRoomCount)
                .requiredRoomCapacity(maxCapacity + reserveCapacity)
                .recommended(recommended)
                .warnings(List.copyOf(warnings))
                .build();
    }

    public List<Integer> distributeEvenly(int totalStudents, int classCount) {
        List<Integer> targetClassSizes = new ArrayList<>(classCount);
        for (int index = 0; index < classCount; index++) {
            targetClassSizes.add(totalStudents / classCount + (index < totalStudents % classCount ? 1 : 0));
        }
        return List.copyOf(targetClassSizes);
    }

    public void requireScenarioAcceptance(
            ClassDivisionScenario scenario, boolean acceptReserve, boolean acceptBelowMinimum, String reason) {
        if ((scenario.reserveUsed() > 0 && !acceptReserve)
                || (scenario.belowMinimum()
                        && (!acceptBelowMinimum || TextsNormalizer.normalizeOptionalText(reason) == null))) {
            throw new AdmissionPlanException(ErrorCode.ADMISSION_WARNING_CONFIRMATION_REQUIRED);
        }
    }

    private int divideRoundUp(int totalStudents, int classCapacity) {
        return (int) ((totalStudents + (long) classCapacity - 1) / classCapacity);
    }

    /**
     * Chọn ít lớp nhất theo thứ tự: không dùng thêm dự phòng, dùng dự phòng, rồi dưới tối thiểu.
     * Sĩ số ENROLLED là cận dưới bất biến; phần tử đầu tương ứng các lớp đã có người nhập học.
     * Lớp đã dùng dự phòng từ trước được giữ nguyên, không buộc chuyển người để chia đều tuyệt đối.
     */
    public List<Integer> selectAutomaticClassSizes(
            int totalStudents,
            List<Integer> enrolledClassSizes,
            int minCapacity,
            int maxCapacity,
            int reserveCapacity) {
        validateCapacityLimits(minCapacity, maxCapacity, reserveCapacity);
        if (totalStudents < 0
                || enrolledClassSizes.stream().anyMatch(size -> size < 1 || size > maxCapacity + reserveCapacity)
                || enrolledClassSizes.stream().mapToInt(Integer::intValue).sum() > totalStudents) {
            throw new AdmissionPlanException(ErrorCode.ADMISSION_INVALID_CAPACITY);
        }
        if (totalStudents == 0) return List.of();
        for (int scenarioPriority = 0; scenarioPriority < 3; scenarioPriority++) {
            boolean allowsReserve = scenarioPriority > 0;
            boolean allowsUnderfilledClasses = scenarioPriority == 2;
            for (int classCount = Math.max(1, enrolledClassSizes.size()); classCount <= 99; classCount++) {
                List<Integer> lowerSizes = new ArrayList<>();
                List<Integer> upperSizes = new ArrayList<>();
                for (int classIndex = 0; classIndex < classCount; classIndex++) {
                    int enrolledCount = classIndex < enrolledClassSizes.size() ? enrolledClassSizes.get(classIndex) : 0;
                    lowerSizes.add(Math.max(enrolledCount, allowsUnderfilledClasses ? 1 : minCapacity));
                    upperSizes.add(Math.max(enrolledCount, maxCapacity + (allowsReserve ? reserveCapacity : 0)));
                }
                int minimumPopulation =
                        lowerSizes.stream().mapToInt(Integer::intValue).sum();
                int maximumPopulation =
                        upperSizes.stream().mapToInt(Integer::intValue).sum();
                if (totalStudents < minimumPopulation || totalStudents > maximumPopulation) continue;
                return fillRemainingSeats(totalStudents, minimumPopulation, lowerSizes, upperSizes);
            }
        }
        return List.of();
    }

    private List<ClassDivisionScenario> buildClassCountScenarios(
            int totalStudents,
            int minimumClassCount,
            int maximumClassCount,
            int minCapacity,
            int maxCapacity,
            int reserveCapacity,
            long suitableRoomCount) {
        List<ClassDivisionScenario> scenarios = new ArrayList<>();
        for (int classCount = minimumClassCount; classCount <= maximumClassCount; classCount++) {
            scenarios.add(buildScenario(
                    totalStudents,
                    classCount,
                    minCapacity,
                    maxCapacity,
                    reserveCapacity,
                    suitableRoomCount,
                    classCount == minimumClassCount));
        }
        return scenarios;
    }

    private List<Integer> fillRemainingSeats(
            int totalStudents, int minimumPopulation, List<Integer> lowerSizes, List<Integer> upperSizes) {
        // Bổ sung vào lớp ít người nhất trong các cận cho phép; không di chuyển người ENROLLED.
        List<Integer> targetSizes = new ArrayList<>(lowerSizes);
        for (int remainingStudents = totalStudents - minimumPopulation; remainingStudents > 0; remainingStudents--) {
            int selectedClassIndex = -1;
            for (int classIndex = 0; classIndex < lowerSizes.size(); classIndex++) {
                if (targetSizes.get(classIndex) < upperSizes.get(classIndex)
                        && (selectedClassIndex < 0
                                || targetSizes.get(classIndex) < targetSizes.get(selectedClassIndex))) {
                    selectedClassIndex = classIndex;
                }
            }
            targetSizes.set(selectedClassIndex, targetSizes.get(selectedClassIndex) + 1);
        }
        return List.copyOf(targetSizes);
    }

    private List<ClassDivisionScenario> buildReserveScenarios(
            int totalStudents,
            int minClassesWithoutReserve,
            int maxClassesAtMinimumCapacity,
            int minCapacity,
            int maxCapacity,
            int reserveCapacity,
            long suitableRoomCount) {
        List<ClassDivisionScenario> scenarios = new ArrayList<>();
        // Case 2: Cho phép sử dụng suất dự phòng, ví dụ 140 / (40+15) = 2.54 --> 3 lớp tối thiểu
        int minClassesWithReserve = divideRoundUp(totalStudents, maxCapacity + reserveCapacity);
        if (minClassesWithReserve <= maxClassesAtMinimumCapacity) {
            scenarios.addAll(buildClassCountScenarios(
                    totalStudents,
                    minClassesWithReserve,
                    maxClassesAtMinimumCapacity,
                    minCapacity,
                    maxCapacity,
                    reserveCapacity,
                    suitableRoomCount));
            // phương án tham khảo ngoài giới hạn upper
            if (minClassesWithoutReserve <= 99)
                scenarios.add(buildScenario(
                        totalStudents,
                        minClassesWithoutReserve,
                        minCapacity,
                        maxCapacity,
                        reserveCapacity,
                        suitableRoomCount,
                        false));
        } else {
            scenarios.add(buildScenario(
                    totalStudents,
                    minClassesWithReserve,
                    minCapacity,
                    maxCapacity,
                    reserveCapacity,
                    suitableRoomCount,
                    true));
        }
        return List.copyOf(scenarios);
    }
}
