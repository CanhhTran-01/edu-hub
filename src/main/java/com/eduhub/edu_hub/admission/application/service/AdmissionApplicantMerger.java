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
    private final AdmissionIdentityPolicy identityPolicy;

    public List<AdmissionApplicant> mergePreservingEnrolledApplicants(
            List<AdmissionApplicant> existingApplicants, List<AdmissionApplicant> incomingApplicants) {

        int nextRowNumber = existingApplicants.stream()
                        .mapToInt(AdmissionApplicant::getRowNumber)
                        .max()
                        .orElse(1)
                + 1;
        Map<String, AdmissionApplicant> existingApplicantsByApplicationCode = new HashMap<>();
        Set<String> processedApplicationCodes = new HashSet<>(); // tập mã đã xử lý, chống xử lý lặp.

        existingApplicants.forEach(applicant -> {
            if (applicant.getApplicationCode() != null)
                existingApplicantsByApplicationCode.putIfAbsent(applicant.getApplicationCode(), applicant);
        });

        for (AdmissionApplicant incomingApplicant : incomingApplicants) {

            AdmissionApplicant existingApplicant =
                    existingApplicantsByApplicationCode.get(incomingApplicant.getApplicationCode());

            // đã gộp rồi, bỏ qua các bản lặp của cùng mã trong file mới.
            if (existingApplicant != null && !processedApplicationCodes.add(incomingApplicant.getApplicationCode()))
                continue;

            // dòng cũ đã ENROLLED, có 3 case:
            if (existingApplicant != null && existingApplicant.getState() == AdmissionApplicantStatus.ENROLLED) {

                // dòng mới là EXISTING và giống dòng cũ --> bỏ qua
                if (incomingApplicant.getState() == AdmissionApplicantStatus.EXISTING
                        && identityPolicy.hasSameApplicantData(existingApplicant, incomingApplicant)) continue;

                // ngược lại, đánh CONFLICT, gán số dòng mới và thêm vào danh sách để Admin thấy
                setApplicantStatus(incomingApplicant);
                incomingApplicant.setRowNumber(nextRowNumber++);
                existingApplicants.add(incomingApplicant);

            } else if (existingApplicant != null) {
                // chép dữ liệu mới đè lên dòng cũ, trừ các trường định danh/audit
                BeanUtils.copyProperties(
                        incomingApplicant,
                        existingApplicant,
                        "id",
                        "createdAt",
                        "updatedAt",
                        "createdBy",
                        "updatedBy",
                        "rowNumber",
                        "plan",
                        "deleted");
            } else {
                incomingApplicant.setRowNumber(nextRowNumber++);
                existingApplicants.add(incomingApplicant);
            }
        }

        return existingApplicants;
    }

    private void setApplicantStatus(AdmissionApplicant applicant) {
        applicant.setState(AdmissionApplicantStatus.CONFLICT);
        applicant.setErrorMessage("Không ghi đè hồ sơ đã nhập học thành công");
    }
}
