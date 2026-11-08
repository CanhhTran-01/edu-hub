package com.eduhub.edu_hub.admission.domain.policy;

import com.eduhub.edu_hub.admission.domain.model.ClassDivisionScenario;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
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
            throw new BaseException(ErrorCode.ADMISSION_INVALID_CAPACITY);
        }
    }

    public List<ClassDivisionScenario> generateScenarios(
            int totalStudents, int minCapacity, int maxCapacity, int reserveCapacity, long suitableRoomCount) {

        validateCapacityLimits(minCapacity, maxCapacity, reserveCapacity);
        if (totalStudents < 0) throw new BaseException(ErrorCode.VALIDATION_ERROR);

        if (totalStudents == 0) return List.of();
        if (divideRoundUp(totalStudents, maxCapacity + reserveCapacity) > 99) return List.of();

        List<ClassDivisionScenario> scenarios = new ArrayList<>();
        int maxClassesAtMinimumCapacity = Math.min(99, totalStudents / minCapacity);

        // Case 1: Không cần suất dự phòng, ví dụ 140 / 40 = 3.5 --> 4 lớp tối thiểu
        int minClassesWithoutReserve = divideRoundUp(totalStudents, maxCapacity);
        if (minClassesWithoutReserve <= maxClassesAtMinimumCapacity) {
            for (int classCount = minClassesWithoutReserve; classCount <= maxClassesAtMinimumCapacity; classCount++) {
                scenarios.add(buildScenario(
                        totalStudents,
                        classCount,
                        minCapacity,
                        maxCapacity,
                        reserveCapacity,
                        suitableRoomCount,
                        classCount == minClassesWithoutReserve));
            }
            return List.copyOf(scenarios);
        }

        // Case 2: Cho phép sử dụng suất dự phòng, ví dụ 140 / (40+15) = 2.54 --> 3 lớp tối thiểu
        int minClassesWithReserve = divideRoundUp(totalStudents, maxCapacity + reserveCapacity);
        if (minClassesWithReserve <= maxClassesAtMinimumCapacity) {
            for (int classCount = minClassesWithReserve; classCount <= maxClassesAtMinimumCapacity; classCount++) {
                scenarios.add(buildScenario(
                        totalStudents,
                        classCount,
                        minCapacity,
                        maxCapacity,
                        reserveCapacity,
                        suitableRoomCount,
                        classCount == minClassesWithReserve));
            }
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

    public ClassDivisionScenario buildScenario(
            int totalStudents,
            int classCount,
            int minCapacity,
            int maxCapacity,
            int reserveCapacity,
            long suitableRoomCount,
            boolean recommended) {

        validateCapacityLimits(minCapacity, maxCapacity, reserveCapacity);
        if (totalStudents < 0 || classCount < 1) throw new BaseException(ErrorCode.VALIDATION_ERROR);

        // phân phối đều total thành count phần bằng nhau nhất, danh sách theo thứ tự giảm dần
        List<Integer> targetClassSizes = distributeEvenly(totalStudents, classCount);
        if (targetClassSizes.getFirst() > maxCapacity + reserveCapacity)
            throw new BaseException(ErrorCode.CLASS_CAPACITY_EXCEEDED);

        List<String> warnings = new ArrayList<>();
        boolean hasUnderfilledClass = targetClassSizes.getLast() < minCapacity;
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
            throw new BaseException(ErrorCode.ADMISSION_WARNING_CONFIRMATION_REQUIRED);
        }
    }

    private int divideRoundUp(int totalStudents, int classCapacity) {
        return (int) ((totalStudents + (long) classCapacity - 1) / classCapacity);
    }
}
