package com.eduhub.edu_hub.admission.domain.policy;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.StudentAdmission;
import com.eduhub.edu_hub.shared.common.TextsNormalizer;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AdmissionIdentityPolicy {

    public boolean hasSameApplicantData(AdmissionApplicant applicant, StudentAdmission admission) {
        return Objects.equals(applicant.getCitizenId(), admission.getCitizenId())
                && TextsNormalizer.normalizeKey(applicant.getFullName())
                        .equals(TextsNormalizer.normalizeKey(admission.getFullName()))
                && Objects.equals(applicant.getDateOfBirth(), admission.getDateOfBirth())
                && TextsNormalizer.normalizeKey(applicant.getGender())
                        .equals(TextsNormalizer.normalizeKey(admission.getGender()))
                && Objects.equals(applicant.getEmail(), TextsNormalizer.normalizeEmail(admission.getEmail()))
                && TextsNormalizer.normalizeKey(applicant.getPhone())
                        .equals(TextsNormalizer.normalizeKey(admission.getPhone()))
                && applicant.getEntranceScore().compareTo(admission.getEntranceScore()) == 0;
    }

    public boolean hasSameApplicantData(AdmissionApplicant existingApplicant, AdmissionApplicant incomingApplicant) {
        return Objects.equals(existingApplicant.getCitizenId(), incomingApplicant.getCitizenId())
                && Objects.equals(existingApplicant.getApplicationCode(), incomingApplicant.getApplicationCode())
                && TextsNormalizer.normalizeKey(existingApplicant.getFullName())
                        .equals(TextsNormalizer.normalizeKey(incomingApplicant.getFullName()))
                && Objects.equals(existingApplicant.getDateOfBirth(), incomingApplicant.getDateOfBirth())
                && TextsNormalizer.normalizeKey(existingApplicant.getGender())
                        .equals(TextsNormalizer.normalizeKey(incomingApplicant.getGender()))
                && Objects.equals(existingApplicant.getEmail(), incomingApplicant.getEmail())
                && TextsNormalizer.normalizeKey(existingApplicant.getPhone())
                        .equals(TextsNormalizer.normalizeKey(incomingApplicant.getPhone()))
                && existingApplicant.getEntranceScore() != null
                && incomingApplicant.getEntranceScore() != null
                && existingApplicant.getEntranceScore().compareTo(incomingApplicant.getEntranceScore()) == 0;
    }

    /** Phân loại một mã hồ sơ từ dữ liệu đã tải, trước khi service đối chiếu CCCD và tài khoản. */
    public void classifyApplicationCode(
            AdmissionApplicant applicant,
            AdmissionApplicant firstApplicantWithSameCode,
            StudentAdmission existingAdmission) {
        // Trùng mã hồ sơ ngay trong file
        if (firstApplicantWithSameCode != null) {
            if (hasSameApplicantData(firstApplicantWithSameCode, applicant)
                    && firstApplicantWithSameCode.getState() != AdmissionApplicantStatus.CONFLICT) {
                setApplicantStatus(applicant, AdmissionApplicantStatus.EXISTING, "Dòng trùng đã tồn tại trong file");
            } else {
                setApplicantStatus(
                        firstApplicantWithSameCode,
                        AdmissionApplicantStatus.CONFLICT,
                        "Cùng mã hồ sơ, khác dữ liệu trong file");
                setApplicantStatus(
                        applicant, AdmissionApplicantStatus.CONFLICT, "Cùng mã hồ sơ, khác dữ liệu trong file");
            }
            return;
        }

        // Mã hồ sơ đã có trong DB
        if (existingAdmission != null) {
            applicant.setStudentId(existingAdmission.getStudentId());
            applicant.setReuseAccount(true);
            boolean hasMatchingApplicantData = hasSameApplicantData(applicant, existingAdmission);
            setApplicantStatus(
                    applicant,
                    hasMatchingApplicantData ? AdmissionApplicantStatus.EXISTING : AdmissionApplicantStatus.CONFLICT,
                    hasMatchingApplicantData ? "Hồ sơ đã tồn tại" : "Cùng mã hồ sơ nhưng khác dữ liệu hoặc CCCD");
        }
    }

    public void applyReusedStudentAccount(
            AdmissionApplicant applicant, Long studentId, String existingFullName, boolean isAccountLocked) {
        applicant.setStudentId(studentId);
        applicant.setReuseAccount(true);

        if (isAccountLocked) {
            applicant.setWarningMessage("Tài khoản cũ đang khóa/ngừng hoạt động; hệ thống giữ nguyên trạng thái");
        }

        if (!TextsNormalizer.normalizeKey(existingFullName)
                .equals(TextsNormalizer.normalizeKey(applicant.getFullName()))) {
            applicant.setWarningMessage("Họ tên khác hồ sơ danh tính cũ; giữ nguyên hồ sơ gốc"
                    + (applicant.getWarningMessage() == null ? "" : "; " + applicant.getWarningMessage()));
        }
    }

    /** Quét toàn bộ tập hồ sơ đã qua validation, kể cả dòng vừa được phân loại EXISTING hoặc CONFLICT. */
    public void markCrossApplicantConflicts(List<AdmissionApplicant> validApplicants) {
        Map<String, List<AdmissionApplicant>> applicantsByCitizenId =
                validApplicants.stream().collect(Collectors.groupingBy(AdmissionApplicant::getCitizenId));
        // Quét xung đột chéo
        // Một CCCD có nhiều mã hồ sơ
        applicantsByCitizenId.values().stream()
                .filter(group -> group.stream()
                                .map(AdmissionApplicant::getApplicationCode)
                                .distinct()
                                .count()
                        > 1)
                .forEach(group -> group.forEach(applicant -> setApplicantStatus(
                        applicant, AdmissionApplicantStatus.CONFLICT, "Một CCCD có nhiều mã hồ sơ ở cùng đợt phân")));

        // Lan truyền xung đột theo mã hồ sơ
        // Nhóm các dòng theo mã hồ sơ; nếu có ít nhất một dòng CONFLICT thì mọi dòng cùng mã đều thành CONFLICT
        // Dòng đã CONFLICT giữ nguyên nguyên nhân; chỉ bổ sung lỗi cho các dòng cùng mã chưa bị đánh lỗi.
        validApplicants.stream()
                .collect(Collectors.groupingBy(AdmissionApplicant::getApplicationCode))
                .values()
                .stream()
                .filter(group ->
                        group.stream().anyMatch(applicant -> applicant.getState() == AdmissionApplicantStatus.CONFLICT))
                .forEach(group -> group.stream()
                        .filter(applicant -> applicant.getState() != AdmissionApplicantStatus.CONFLICT)
                        .forEach(applicant -> setApplicantStatus(
                                applicant,
                                AdmissionApplicantStatus.CONFLICT,
                                "Cùng mã hồ sơ nhưng có dữ liệu xung đột")));
    }

    private void setApplicantStatus(
            AdmissionApplicant applicant, AdmissionApplicantStatus status, String errorMessage) {
        applicant.setState(status);
        applicant.setErrorMessage(errorMessage);
    }

    /** Người đã ENROLLED là mốc đối chiếu bất biến, chỉ đánh lỗi trên dòng còn chờ hoặc được bỏ qua. */
    public void markEnrolledApplicantConflicts(List<AdmissionApplicant> applicants) {
        Set<String> enrolledCitizenIds = new HashSet<>();
        applicants.stream()
                .filter(applicant -> applicant.getState() == AdmissionApplicantStatus.ENROLLED)
                .map(AdmissionApplicant::getCitizenId)
                .forEach(enrolledCitizenIds::add);
        for (var applicant : applicants) {
            if (applicant.getState() == AdmissionApplicantStatus.VALID
                    && enrolledCitizenIds.contains(applicant.getCitizenId())) {
                setApplicantStatus(
                        applicant, AdmissionApplicantStatus.CONFLICT, "CCCD đã nhập học trong phương án này");
            }
        }
    }
}
