package com.eduhub.edu_hub.admission.domain.policy;

import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.StudentAdmission;
import com.eduhub.edu_hub.shared.text.PersonText;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class AdmissionIdentityPolicy {

    public boolean same(AdmissionApplicant applicant, StudentAdmission admission) {
        return Objects.equals(applicant.getCitizenId(), admission.getCitizenId())
                && PersonText.key(applicant.getFullName()).equals(PersonText.key(admission.getFullName()))
                && Objects.equals(applicant.getDateOfBirth(), admission.getDateOfBirth())
                && PersonText.key(applicant.getGender()).equals(PersonText.key(admission.getGender()))
                && Objects.equals(applicant.getEmail(), PersonText.email(admission.getEmail()))
                && PersonText.key(applicant.getPhone()).equals(PersonText.key(admission.getPhone()))
                && applicant.getEntranceScore().compareTo(admission.getEntranceScore()) == 0;
    }

    public boolean same(AdmissionApplicant first, AdmissionApplicant second) {
        return Objects.equals(first.getCitizenId(), second.getCitizenId())
                && Objects.equals(first.getApplicationCode(), second.getApplicationCode())
                && PersonText.key(first.getFullName()).equals(PersonText.key(second.getFullName()))
                && Objects.equals(first.getDateOfBirth(), second.getDateOfBirth())
                && PersonText.key(first.getGender()).equals(PersonText.key(second.getGender()))
                && Objects.equals(first.getEmail(), second.getEmail())
                && PersonText.key(first.getPhone()).equals(PersonText.key(second.getPhone()))
                && first.getEntranceScore().compareTo(second.getEntranceScore()) == 0;
    }
}
