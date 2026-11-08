package com.eduhub.edu_hub.admission.domain.policy;

import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.StudentAdmission;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import java.util.Objects;
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
                && existingApplicant.getEntranceScore().compareTo(incomingApplicant.getEntranceScore()) == 0;
    }
}
