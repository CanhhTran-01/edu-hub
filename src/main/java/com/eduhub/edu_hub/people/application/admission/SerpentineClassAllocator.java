package com.eduhub.edu_hub.people.application.admission;

import static com.eduhub.edu_hub.people.application.admission.AdmissionNamePolicy.MAX_SAME_NAME_PER_CLASS;

import com.eduhub.edu_hub.people.domain.model.*;
import com.eduhub.edu_hub.shared.exception.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class SerpentineClassAllocator {

    /*
    Đây là bộ phân bổ thí sinh vào lớp theo kiểu “rắn” (snake draft):
    xếp thí sinh theo điểm từ cao xuống thấp,
    rồi chia lần lượt vào các lớp theo chiều đi tới rồi đi lùi, để các lớp có chất lượng đầu vào đều nhau.
    */

    public void assign(List<AdmissionApplicant> applicants, List<AdmissionPlanClass> slots) {
        assign(applicants, slots, List.of());
    }

    public void assign(
            List<AdmissionApplicant> applicants, List<AdmissionPlanClass> slots, List<AdmissionApplicant> enrolled) {

        if (slots.isEmpty()
                || slots.stream().mapToInt(AdmissionPlanClass::getTargetSize).sum() != applicants.size()) {
            // Tổng chỗ còn lại của các lớp phải bằng đúng số thí sinh cần phân.
            throw new BaseException(ErrorCode.ADMISSION_INVALID_CAPACITY);
        }

        // Điểm đầu vào giảm dần, bằng điểm thì xếp theo fullName tăng dần
        List<AdmissionApplicant> ordered = applicants.stream()
                .sorted(Comparator.comparing(AdmissionApplicant::getEntranceScore)
                        .reversed()
                        .thenComparing(AdmissionApplicant::getFullName))
                .toList();

        // thuật toán phân bổ kiểu rắn
        // Ví dụ điểm giảm dần: SV1→A, SV2→B, SV3→C, SV4→C, SV5→B, SV6→A, SV7→A,SV8→B ...
        int[] assigned = new int[slots.size()];
        long turn = 0;
        for (AdmissionApplicant applicant : ordered) {
            while (true) {
                // phase < n    = đi tới   (0 → n-1)
                // phase >= n   = đi lùi   (n-1 → 0)
                int phase = (int) (turn++ % (2L * slots.size()));
                int index = phase < slots.size() ? phase : 2 * slots.size() - 1 - phase;

                // lớp đã đầy, bỏ qua lớp đầy và thử vị trí kế tiếp trong chu kỳ
                if (assigned[index] >= slots.get(index).getTargetSize()) continue;

                // gặp lớp còn chỗ thì gán, tăng assigned[index], break sang thí sinh tiếp theo
                applicant.setAdministrativeClass(slots.get(index).getAdministrativeClass());
                assigned[index]++;
                break;
            }
        }

        // Gộp người đã nhập học và người mới phân
        List<AdmissionApplicant> all = new ArrayList<>(enrolled);
        all.addAll(ordered);

        // tách sinh viên trùng họ tên trong cùng một lớp
        resolveNames(all, slots);
    }

    private void resolveNames(List<AdmissionApplicant> applicants, List<AdmissionPlanClass> slots) {

        for (int index = 0; index < slots.size(); index++) {

            // Với mỗi lớp index, duyệt các thí sinh trong lớp đó
            Long classId = slots.get(index).getAdministrativeClass().getId();

            // Đếm số người theo từng tên trong lớp
            Map<String, Integer> nameCount = new HashMap<>();

            for (AdmissionApplicant duplicate : inClass(applicants, classId)) {

                String name = AdmissionText.key(duplicate.getFullName()); // lấy tên thí sinh

                // Tăng đếm; chưa vượt giới hạn thì chấp nhận bỏ qua
                if (nameCount.merge(name, 1, Integer::sum) <= MAX_SAME_NAME_PER_CLASS) continue;

                if (!duplicate.isPending()) continue; // Vượt giới hạn nhưng đã nhập học thì không được đổi lớp

                // Tìm người để đổi chỗ
                AdmissionApplicant best = null;
                BigDecimal smallestDifference = null;

                for (int neighbor : new int[] {index - 1, index + 1}) {

                    // Chỉ xét hai lớp lân cận: index - 1 và index + 1
                    if (neighbor < 0 || neighbor >= slots.size()) continue;

                    Long neighborId =
                            slots.get(neighbor).getAdministrativeClass().getId();

                    // Lớp kề đã đủ 3 người tên này thì bỏ qua, không chuyển sang lớp này
                    if (countName(applicants, neighborId, name) >= MAX_SAME_NAME_PER_CLASS) continue;

                    for (AdmissionApplicant candidate : inClass(applicants, neighborId)) {

                        if (!candidate.isPending()) continue; // Không hoán đổi sinh viên đã hoàn tất nhập học.

                        // ứng viên chuyển sang lớp hiện tại không được làm lớp này vượt giới hạn
                        String candidateName = AdmissionText.key(candidate.getFullName());
                        if (countName(applicants, classId, candidateName) >= MAX_SAME_NAME_PER_CLASS) continue;

                        // Trong các ứng viên hợp lệ, chọn người có chênh lệch điểm nhỏ nhất với duplicate
                        BigDecimal difference = duplicate
                                .getEntranceScore()
                                .subtract(candidate.getEntranceScore())
                                .abs();

                        if (smallestDifference == null || difference.compareTo(smallestDifference) < 0) {
                            best = candidate;
                            smallestDifference = difference;
                        }
                    }
                }

                // hoán đổi, Nếu không có best (không ai phù hợp) thì để nguyên, chấp nhận trùng tên.
                if (best != null) {
                    AdministrativeClass original = duplicate.getAdministrativeClass();
                    duplicate.setAdministrativeClass(best.getAdministrativeClass());
                    best.setAdministrativeClass(original);

                    nameCount.merge(name, -1, Integer::sum); // duplicate rời lớp: -1
                    nameCount.merge(AdmissionText.key(best.getFullName()), 1, Integer::sum); // best vào lớp: +1
                }
            }
        }
    }

    private List<AdmissionApplicant> inClass(List<AdmissionApplicant> rows, Long id) {
        // lấy danh sách sinh viên đang thuộc lớp có id này
        return rows.stream()
                .filter(row -> row.getAdministrativeClass().getId().equals(id))
                .toList();
    }

    private long countName(List<AdmissionApplicant> rows, Long id, String name) {
        // đếm số sinh viên có tên trùng với "name"
        return rows.stream()
                .filter(row -> row.getAdministrativeClass().getId().equals(id)
                        && AdmissionText.key(row.getFullName()).equals(name))
                .count();
    }
}
