package com.eduhub.edu_hub.admission.domain.policy;

import com.eduhub.edu_hub.admission.domain.model.ClassDivisionScenario;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.text.PersonText;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AdmissionCapacityPolicy {

    public void validate(int minimum, int maximum, int reserve) {
        if (minimum < 1 || maximum < minimum || reserve < 0 || maximum > 99 || reserve > 99 - maximum) {
            throw new BaseException(ErrorCode.ADMISSION_INVALID_CAPACITY);
        }
    }

    public List<ClassDivisionScenario> scenarios(int total, int minimum, int maximum, int reserve, long rooms) {
        validate(minimum, maximum, reserve);
        if (total < 0) throw new BaseException(ErrorCode.VALIDATION_ERROR);
        if (total == 0) return List.of();
        if (ceil(total, maximum + reserve) > 99) return List.of();
        List<ClassDivisionScenario> result = new ArrayList<>();
        int lower = ceil(total, maximum);
        int upper = Math.min(99, total / minimum);
        if (lower <= upper) {
            for (int count = lower; count <= upper; count++) {
                result.add(scenario(total, count, minimum, maximum, reserve, rooms, count == lower));
            }
            return List.copyOf(result);
        }
        int extendedLower = ceil(total, maximum + reserve);
        if (extendedLower <= upper) {
            for (int count = extendedLower; count <= upper; count++) {
                result.add(scenario(total, count, minimum, maximum, reserve, rooms, count == extendedLower));
            }
            // A below-minimum alternative helps the admin compare reserve use with smaller classes.
            if (lower <= 99) result.add(scenario(total, lower, minimum, maximum, reserve, rooms, false));
        } else {
            result.add(scenario(total, extendedLower, minimum, maximum, reserve, rooms, true));
        }
        return List.copyOf(result);
    }

    public ClassDivisionScenario scenario(
            int total, int count, int minimum, int maximum, int reserve, long rooms, boolean recommended) {
        validate(minimum, maximum, reserve);
        if (total < 0 || count < 1) throw new BaseException(ErrorCode.VALIDATION_ERROR);
        List<Integer> sizes = sizes(total, count);
        if (sizes.getFirst() > maximum + reserve) throw new BaseException(ErrorCode.CLASS_CAPACITY_EXCEEDED);
        boolean below = sizes.getLast() < minimum;
        int used = sizes.stream().mapToInt(size -> Math.max(0, size - maximum)).sum();
        List<String> warnings = new ArrayList<>();
        if (used > 0) warnings.add("Phương án sử dụng " + used + " suất dự phòng");
        if (below) warnings.add("Có lớp có sĩ số dưới mức tối thiểu");
        if (rooms < count) warnings.add("Thiếu phòng có sức chứa từ " + (maximum + reserve) + " chỗ");
        if (count > 99) warnings.add("Số lớp vượt phạm vi định danh hai chữ số");
        return ClassDivisionScenario.builder()
                .classCount(count)
                .targetSizes(sizes)
                .level(below ? "BELOW_MINIMUM" : used > 0 ? "RESERVE" : "NORMAL")
                .reserveUsed(used)
                .belowMinimum(below)
                .insufficientRooms(rooms < count)
                .suitableRooms(rooms)
                .requiredRoomCapacity(maximum + reserve)
                .recommended(recommended)
                .warnings(List.copyOf(warnings))
                .build();
    }

    public List<Integer> sizes(int total, int count) {

        if (total < 0 || count < 1) throw new BaseException(ErrorCode.VALIDATION_ERROR);

        List<Integer> sizes = new ArrayList<>(count);

        for (int index = 0; index < count; index++) {
            sizes.add(total / count + (index < total % count ? 1 : 0));
        }

        return List.copyOf(sizes);
    }

    public void requireAcceptance(
            ClassDivisionScenario scenario, boolean acceptReserve, boolean acceptBelow, String reason) {
        if ((scenario.reserveUsed() > 0 && !acceptReserve)
                || (scenario.belowMinimum() && (!acceptBelow || PersonText.optional(reason) == null))) {
            throw new BaseException(ErrorCode.ADMISSION_WARNING_CONFIRMATION_REQUIRED);
        }
    }

    private int ceil(int total, int capacity) {
        return (int) ((total + (long) capacity - 1) / capacity);
    }
}
