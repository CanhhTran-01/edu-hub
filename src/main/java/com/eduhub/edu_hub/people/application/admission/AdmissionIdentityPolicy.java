package com.eduhub.edu_hub.people.application.admission;

import com.eduhub.edu_hub.people.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.people.domain.model.StudentAdmission;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdmissionIdentityPolicy {
    private final Validator validator;

    private record EmailValue(@Email String value) {}

    public List<String> validate(AdmissionApplicant applicant) {

        List<String> errors = new ArrayList<>();

        if (applicant.getApplicationCode() == null
                || applicant.getApplicationCode().isBlank()
                || applicant.getApplicationCode().length() > 100) errors.add("Mã hồ sơ bắt buộc, tối đa 100 ký tự");

        if (applicant.getCitizenId() == null || !applicant.getCitizenId().matches("[0-9]{12}")) {
            errors.add("CCCD bắt buộc và phải gồm 12 chữ số");
        }

        if (applicant.getFullName() == null || applicant.getFullName().length() > 255) {
            errors.add("Họ tên bắt buộc, tối đa 255 ký tự");
        }

        if (applicant.getEntranceScore() == null
                || applicant.getEntranceScore().signum() < 0
                || applicant.getEntranceScore().scale() > 2
                || applicant.getEntranceScore().precision() > 19
                || applicant.getEntranceScore().precision() - applicant.getEntranceScore().scale() > 17) {
            errors.add("Điểm đầu vào bắt buộc, không âm, tối đa 2 chữ số thập phân");
        }

        if (applicant.getEmail() != null
                && (applicant.getEmail().length() > 255
                        || !validator.validate(new EmailValue(applicant.getEmail())).isEmpty())) {
            errors.add("Email sai định dạng");
        }

        if (applicant.getPhone() != null && !applicant.getPhone().matches("\\+?[0-9 ()-]{7,32}")) {
            errors.add("Số điện thoại sai định dạng");
        }

        if (applicant.getDateOfBirth() != null && !applicant.getDateOfBirth().isBefore(LocalDate.now())) {
            errors.add("Ngày sinh phải nằm trong quá khứ");
        }

        if (applicant.getGender() != null
                && !List.of("NAM", "NỮ", "NU", "KHÁC", "KHAC", "MALE", "FEMALE", "OTHER")
                        .contains(AdmissionText.key(applicant.getGender()))) errors.add("Giới tính không hợp lệ");

        return List.copyOf(errors);
    }

    public boolean same(AdmissionApplicant applicant, StudentAdmission admission) {
        return Objects.equals(applicant.getCitizenId(), admission.getCitizenId())
                && AdmissionText.key(applicant.getFullName()).equals(AdmissionText.key(admission.getFullName()))
                && Objects.equals(applicant.getDateOfBirth(), admission.getDateOfBirth())
                && AdmissionText.key(applicant.getGender()).equals(AdmissionText.key(admission.getGender()))
                && Objects.equals(applicant.getEmail(), AdmissionText.email(admission.getEmail()))
                && AdmissionText.key(applicant.getPhone()).equals(AdmissionText.key(admission.getPhone()))
                && applicant.getEntranceScore().compareTo(admission.getEntranceScore()) == 0;
    }

    public boolean same(AdmissionApplicant first, AdmissionApplicant second) {
        return Objects.equals(first.getCitizenId(), second.getCitizenId())
                && Objects.equals(first.getApplicationCode(), second.getApplicationCode())
                && AdmissionText.key(first.getFullName()).equals(AdmissionText.key(second.getFullName()))
                && Objects.equals(first.getDateOfBirth(), second.getDateOfBirth())
                && AdmissionText.key(first.getGender()).equals(AdmissionText.key(second.getGender()))
                && Objects.equals(first.getEmail(), second.getEmail())
                && AdmissionText.key(first.getPhone()).equals(AdmissionText.key(second.getPhone()))
                && first.getEntranceScore().compareTo(second.getEntranceScore()) == 0;
    }
}
