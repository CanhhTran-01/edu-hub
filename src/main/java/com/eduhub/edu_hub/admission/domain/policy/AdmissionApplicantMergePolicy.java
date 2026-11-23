package com.eduhub.edu_hub.admission.domain.policy;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Gộp danh sách đã tải, giữ nguyên instance và dữ liệu của hồ sơ đã nhập học thành công. */
@Component
@RequiredArgsConstructor
public class AdmissionApplicantMergePolicy {
    private final AdmissionIdentityPolicy identityPolicy;

    /**
     * File bổ sung chỉ thêm dòng mới. Bản giống dữ liệu cũ được bỏ qua; mã/CCCD mâu thuẫn chỉ đánh lỗi
     * trên dòng mới. Mọi dòng cũ, kể cả INVALID/CONFLICT, phải được sửa bằng applicantId qua API riêng.
     *
     * @param existingApplicants các dòng chưa xóa đang thuộc phương án
     * @param incomingApplicants các dòng đã được đọc, kiểm tra và đối chiếu từ file mới
     * @param maximumStoredRowNumber số dòng lớn nhất của toàn bộ lịch sử, kể cả dòng đã xóa mềm; 0 khi chưa có dòng
     */
    public List<AdmissionApplicant> appendApplicants(
            List<AdmissionApplicant> existingApplicants,
            List<AdmissionApplicant> incomingApplicants,
            int maximumStoredRowNumber) {
        List<AdmissionApplicant> combinedApplicants = new ArrayList<>(existingApplicants);
        int nextRowNumber = maximumStoredRowNumber + 1;
        Map<String, List<AdmissionApplicant>> applicantsByApplicationCode = existingApplicants.stream()
                .filter(applicant -> applicant.getApplicationCode() != null)
                .collect(Collectors.groupingBy(AdmissionApplicant::getApplicationCode));
        Set<String> existingCitizenIds = existingApplicants.stream()
                .map(AdmissionApplicant::getCitizenId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (AdmissionApplicant incomingApplicant : incomingApplicants) {
            var applicantsWithSameCode =
                    applicantsByApplicationCode.getOrDefault(incomingApplicant.getApplicationCode(), List.of());
            if (incomingApplicant.getState() != AdmissionApplicantStatus.INVALID && !applicantsWithSameCode.isEmpty()) {
                boolean hasEquivalentApplicant = applicantsWithSameCode.stream()
                        .anyMatch(existingApplicant -> existingApplicant.getState() != AdmissionApplicantStatus.INVALID
                                && identityPolicy.hasSameApplicantData(existingApplicant, incomingApplicant));
                // đã gộp rồi, bỏ qua các bản lặp của cùng mã trong file mới.
                if (hasEquivalentApplicant) continue;
                markConflict(incomingApplicant, "Mã hồ sơ đã có trong phương án; dùng chức năng sửa hồ sơ lỗi");
            } else if (incomingApplicant.getState() != AdmissionApplicantStatus.INVALID
                    && existingCitizenIds.contains(incomingApplicant.getCitizenId())) {
                markConflict(incomingApplicant, "CCCD đã có trong phương án với mã hồ sơ khác");
            }
            // Dòng bổ sung dùng số thứ tự tiếp theo trong plan để không trùng unique (plan_id, row_number).
            // Số dòng của lần upload đầu tiên vẫn giữ vị trí vật lý trong Excel.
            // Vẫn nối sau dòng đã xóa mềm, kể cả khi toàn bộ danh sách đang xử lý đã bị bỏ.
            if (maximumStoredRowNumber > 0) incomingApplicant.setRowNumber(nextRowNumber++);
            combinedApplicants.add(incomingApplicant);
        }
        return combinedApplicants;
    }

    private void markConflict(AdmissionApplicant applicant, String message) {
        applicant.setState(AdmissionApplicantStatus.CONFLICT);
        applicant.setErrorMessage(message);
    }
}
