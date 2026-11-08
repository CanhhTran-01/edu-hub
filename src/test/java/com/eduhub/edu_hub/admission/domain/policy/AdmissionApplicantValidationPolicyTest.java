package com.eduhub.edu_hub.admission.domain.policy;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdmissionApplicantValidationPolicyTest {
    private static final LocalDate VALIDATION_DATE = LocalDate.of(2026, 11, 9);

    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private final AdmissionApplicantValidationPolicy validationPolicy =
            new AdmissionApplicantValidationPolicy(validatorFactory.getValidator());

    @AfterEach
    void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void allowsMissingOptionalFieldsAndPreservesLeadingCitizenIdZero() {
        AdmissionApplicant applicant = validApplicant();

        assertThat(validationPolicy.validateApplicant(applicant, VALIDATION_DATE))
                .isEmpty();
        assertThat(applicant.getCitizenId()).isEqualTo("012345678901");
    }

    @Test
    void evaluatesDateOfBirthAgainstTheDateProvidedByTheService() {
        AdmissionApplicant applicant = validApplicant();
        applicant.setDateOfBirth(VALIDATION_DATE);

        assertThat(validationPolicy.validateApplicant(applicant, VALIDATION_DATE))
                .containsExactly("Ngày sinh phải nằm trong quá khứ");
        assertThat(validationPolicy.validateApplicant(applicant, VALIDATION_DATE.plusDays(1)))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "25.001", "100000000000000000.00"})
    void rejectsScoresOutsideTheExistingStorageLimits(String entranceScore) {
        AdmissionApplicant applicant = validApplicant();
        applicant.setEntranceScore(new BigDecimal(entranceScore));

        assertThat(validationPolicy.validateApplicant(applicant, VALIDATION_DATE))
                .containsExactly("Điểm đầu vào bắt buộc, không âm, tối đa 2 chữ số thập phân");
    }

    @Test
    void accumulatesContactAndGenderErrorsWithoutStoppingAtTheFirstField() {
        AdmissionApplicant applicant = validApplicant();
        applicant.setEmail("invalid-email");
        applicant.setPhone("abc");
        applicant.setGender("UNKNOWN");

        assertThat(validationPolicy.validateApplicant(applicant, VALIDATION_DATE))
                .containsExactly("Email sai định dạng", "Số điện thoại sai định dạng", "Giới tính không hợp lệ");
    }

    private AdmissionApplicant validApplicant() {
        return AdmissionApplicant.builder()
                .applicationCode("HS01")
                .citizenId("012345678901")
                .fullName("Nguyễn Văn An")
                .entranceScore(new BigDecimal("25.00"))
                .build();
    }
}
