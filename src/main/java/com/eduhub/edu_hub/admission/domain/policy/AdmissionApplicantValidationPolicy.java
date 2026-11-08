package com.eduhub.edu_hub.admission.domain.policy;

import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Kiểm tra hồ sơ từ dữ liệu và ngày đánh giá do service cung cấp, không truy vấn danh tính. */
@Component
@RequiredArgsConstructor
public class AdmissionApplicantValidationPolicy {
    private final Validator beanValidator;

    private record ApplicantEmail(@Email String email) {}

    public List<String> validateApplicant(AdmissionApplicant applicant, LocalDate validationDate) {

        List<String> validationErrors = new ArrayList<>();

        if (applicant.getApplicationCode() == null
                || applicant.getApplicationCode().isBlank()
                || applicant.getApplicationCode().length() > 100)
            validationErrors.add("Mã hồ sơ bắt buộc, tối đa 100 ký tự");

        if (applicant.getCitizenId() == null || !applicant.getCitizenId().matches("[0-9]{12}")) {
            validationErrors.add("CCCD bắt buộc và phải gồm 12 chữ số");
        }

        if (applicant.getFullName() == null || applicant.getFullName().length() > 255) {
            validationErrors.add("Họ tên bắt buộc, tối đa 255 ký tự");
        }

        if (applicant.getEntranceScore() == null
                || applicant.getEntranceScore().signum() < 0
                || applicant.getEntranceScore().scale() > 2
                || applicant.getEntranceScore().precision() > 19
                || applicant.getEntranceScore().precision()
                                - applicant.getEntranceScore().scale()
                        > 17) {
            validationErrors.add("Điểm đầu vào bắt buộc, không âm, tối đa 2 chữ số thập phân");
        }

        if (applicant.getEmail() != null
                && (applicant.getEmail().length() > 255
                        || !beanValidator
                                .validate(new ApplicantEmail(applicant.getEmail()))
                                .isEmpty())) {
            validationErrors.add("Email sai định dạng");
        }

        if (applicant.getPhone() != null && !applicant.getPhone().matches("\\+?[0-9 ()-]{7,32}")) {
            validationErrors.add("Số điện thoại sai định dạng");
        }

        if (applicant.getDateOfBirth() != null && !applicant.getDateOfBirth().isBefore(validationDate)) {
            validationErrors.add("Ngày sinh phải nằm trong quá khứ");
        }

        if (applicant.getGender() != null
                && !List.of("NAM", "NỮ", "NU", "KHÁC", "KHAC", "MALE", "FEMALE", "OTHER")
                        .contains(TextsNormalizer.normalizeKey(applicant.getGender())))
            validationErrors.add("Giới tính không hợp lệ");

        return List.copyOf(validationErrors);
    }
}
