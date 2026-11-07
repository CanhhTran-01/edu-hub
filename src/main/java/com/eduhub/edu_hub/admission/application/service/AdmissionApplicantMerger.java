package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionIdentityPolicy;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdmissionApplicantMerger {
    private final AdmissionIdentityPolicy policy;

    public List<AdmissionApplicant> merge(List<AdmissionApplicant> previousRows, List<AdmissionApplicant> rows) {

        int nextRow = previousRows.stream()
                        .mapToInt(AdmissionApplicant::getRowNumber)
                        .max()
                        .orElse(1)
                + 1;
        Map<String, AdmissionApplicant> previousByCode = new HashMap<>();
        Set<String> mergedCodes = new HashSet<>(); // tập mã đã xử lý, chống xử lý lặp.

        previousRows.forEach(row -> {
            if (row.getApplicationCode() != null) previousByCode.putIfAbsent(row.getApplicationCode(), row);
        });

        for (AdmissionApplicant incoming : rows) {

            AdmissionApplicant previous = previousByCode.get(incoming.getApplicationCode());

            // đã gộp rồi, bỏ qua các bản lặp của cùng mã trong file mới.
            if (previous != null && !mergedCodes.add(incoming.getApplicationCode())) continue;

            // dòng cũ đã ENROLLED, có 3 case:
            if (previous != null && previous.getState() == AdmissionApplicantStatus.ENROLLED) {

                // dòng mới là EXISTING và giống dòng cũ --> bỏ qua
                if (incoming.getState() == AdmissionApplicantStatus.EXISTING && policy.same(previous, incoming))
                    continue;

                // ngược lại, đánh CONFLICT, gán số dòng mới và thêm vào danh sách để Admin thấy
                mark(incoming, AdmissionApplicantStatus.CONFLICT, "Không ghi đè hồ sơ đã nhập học thành công");
                incoming.setRowNumber(nextRow++);
                previousRows.add(incoming);

            } else if (previous != null) {
                // chép dữ liệu mới đè lên dòng cũ, trừ các trường định danh/audit
                BeanUtils.copyProperties(
                        incoming,
                        previous,
                        "id",
                        "createdAt",
                        "updatedAt",
                        "createdBy",
                        "updatedBy",
                        "rowNumber",
                        "plan",
                        "deleted");
            } else {
                incoming.setRowNumber(nextRow++);
                previousRows.add(incoming);
            }
        }

        return previousRows;
    }

    private void mark(AdmissionApplicant row, AdmissionApplicantStatus state, String error) {
        row.setState(state);
        row.setErrorMessage(error);
    }
}
