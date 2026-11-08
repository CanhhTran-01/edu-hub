package com.eduhub.edu_hub.admission.domain.policy;

import static com.eduhub.edu_hub.admission.domain.policy.AdmissionDuplicateNamePolicy.MAX_SAME_NAME_PER_CLASS;

import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlanClass;
import com.eduhub.edu_hub.shared.exception.*;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
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

    public void allocateByScore(List<AdmissionApplicant> applicants, List<AdmissionPlanClass> classCapacities) {
        allocateByScore(applicants, classCapacities, List.of());
    }

    public void allocateByScore(
            List<AdmissionApplicant> applicants,
            List<AdmissionPlanClass> classCapacities,
            List<AdmissionApplicant> enrolledApplicants) {

        if (classCapacities.isEmpty()
                || classCapacities.stream()
                                .mapToInt(AdmissionPlanClass::getTargetSize)
                                .sum()
                        != applicants.size()) {
            // Tổng chỗ còn lại của các lớp phải bằng đúng số thí sinh cần phân.
            throw new AdmissionPlanException(ErrorCode.ADMISSION_INVALID_CAPACITY);
        }

        // Điểm đầu vào giảm dần, bằng điểm thì xếp theo fullName tăng dần
        List<AdmissionApplicant> applicantsByDescendingScore = applicants.stream()
                .sorted(Comparator.comparing(AdmissionApplicant::getEntranceScore)
                        .reversed()
                        .thenComparing(AdmissionApplicant::getFullName))
                .toList();

        // thuật toán phân bổ kiểu rắn
        // Ví dụ điểm giảm dần: SV1→A, SV2→B, SV3→C, SV4→C, SV5→B, SV6→A, SV7→A,SV8→B ...
        int[] assignedCountsByClass = new int[classCapacities.size()];
        long turn = 0;
        for (AdmissionApplicant applicant : applicantsByDescendingScore) {
            while (true) {
                // phase < n    = đi tới   (0 → n-1)
                // phase >= n   = đi lùi   (n-1 → 0)
                int phase = (int) (turn++ % (2L * classCapacities.size()));
                int index = phase < classCapacities.size() ? phase : 2 * classCapacities.size() - 1 - phase;

                // lớp đã đầy, bỏ qua lớp đầy và thử vị trí kế tiếp trong chu kỳ
                if (assignedCountsByClass[index] >= classCapacities.get(index).getTargetSize()) continue;

                // gặp lớp còn chỗ thì gán, tăng assigned[index], break sang thí sinh tiếp theo
                applicant.setAdministrativeClassId(classCapacities.get(index).getAdministrativeClassId());
                assignedCountsByClass[index]++;
                break;
            }
        }

        // Gộp người đã nhập học và người mới phân
        List<AdmissionApplicant> allApplicants = new ArrayList<>(enrolledApplicants);
        allApplicants.addAll(applicantsByDescendingScore);

        // tách sinh viên trùng họ tên trong cùng một lớp
        redistributeDuplicateNames(allApplicants, classCapacities);
    }

    private void redistributeDuplicateNames(
            List<AdmissionApplicant> applicants, List<AdmissionPlanClass> classCapacities) {

        for (int index = 0; index < classCapacities.size(); index++) {

            // Với mỗi lớp index, duyệt các thí sinh trong lớp đó
            Long classId = classCapacities.get(index).getAdministrativeClassId();

            // Đếm số người theo từng tên trong lớp
            Map<String, Integer> countsByNormalizedName = new HashMap<>();

            for (AdmissionApplicant duplicateNameApplicant : findApplicantsInClass(applicants, classId)) {

                String name = TextsNormalizer.normalizeKey(duplicateNameApplicant.getFullName()); // lấy tên thí sinh

                // Tăng đếm; chưa vượt giới hạn thì chấp nhận bỏ qua
                if (countsByNormalizedName.merge(name, 1, Integer::sum) <= MAX_SAME_NAME_PER_CLASS) continue;

                if (!duplicateNameApplicant.isPendingEnrollment())
                    continue; // Vượt giới hạn nhưng đã nhập học thì không được đổi lớp

                // Tìm người để đổi chỗ
                AdmissionApplicant best = null;
                BigDecimal smallestScoreDifference = null;

                for (int neighborIndex : new int[] {index - 1, index + 1}) {

                    // Chỉ xét hai lớp lân cận: index - 1 và index + 1
                    if (neighborIndex < 0 || neighborIndex >= classCapacities.size()) continue;

                    Long neighborClassId = classCapacities.get(neighborIndex).getAdministrativeClassId();

                    // Lớp kề đã đủ 3 người tên này thì bỏ qua, không chuyển sang lớp này
                    if (countApplicantsWithName(applicants, neighborClassId, name) >= MAX_SAME_NAME_PER_CLASS) continue;

                    for (AdmissionApplicant candidate : findApplicantsInClass(applicants, neighborClassId)) {

                        if (!candidate.isPendingEnrollment())
                            continue; // Không hoán đổi sinh viên đã hoàn tất nhập học.

                        // ứng viên chuyển sang lớp hiện tại không được làm lớp này vượt giới hạn
                        String candidateName = TextsNormalizer.normalizeKey(candidate.getFullName());
                        if (countApplicantsWithName(applicants, classId, candidateName) >= MAX_SAME_NAME_PER_CLASS)
                            continue;

                        // Trong các ứng viên hợp lệ, chọn người có chênh lệch điểm nhỏ nhất với duplicate
                        BigDecimal scoreDifference = duplicateNameApplicant
                                .getEntranceScore()
                                .subtract(candidate.getEntranceScore())
                                .abs();

                        if (smallestScoreDifference == null || scoreDifference.compareTo(smallestScoreDifference) < 0) {
                            best = candidate;
                            smallestScoreDifference = scoreDifference;
                        }
                    }
                }

                // hoán đổi, Nếu không có best (không ai phù hợp) thì để nguyên, chấp nhận trùng tên.
                if (best != null) {
                    Long originalClassId = duplicateNameApplicant.getAdministrativeClassId();
                    duplicateNameApplicant.setAdministrativeClassId(best.getAdministrativeClassId());
                    best.setAdministrativeClassId(originalClassId);

                    countsByNormalizedName.merge(name, -1, Integer::sum); // duplicate rời lớp: -1
                    countsByNormalizedName.merge(
                            TextsNormalizer.normalizeKey(best.getFullName()), 1, Integer::sum); // best vào lớp: +1
                }
            }
        }
    }

    private List<AdmissionApplicant> findApplicantsInClass(List<AdmissionApplicant> applicants, Long classId) {
        // lấy danh sách sinh viên đang thuộc lớp có id này
        return applicants.stream()
                .filter(row -> row.getAdministrativeClassId().equals(classId))
                .toList();
    }

    private long countApplicantsWithName(List<AdmissionApplicant> applicants, Long classId, String name) {
        // đếm số sinh viên có tên trùng với "name"
        return applicants.stream()
                .filter(row -> row.getAdministrativeClassId().equals(classId)
                        && TextsNormalizer.normalizeKey(row.getFullName()).equals(name))
                .count();
    }
}
