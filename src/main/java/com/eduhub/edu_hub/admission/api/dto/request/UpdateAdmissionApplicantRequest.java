package com.eduhub.edu_hub.admission.api.dto.request;

import com.eduhub.edu_hub.admission.application.exception.AdmissionApplicantException;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import lombok.Getter;

/** PATCH: field vắng mặt giữ nguyên; null xóa field tùy chọn và bị từ chối với dữ liệu bắt buộc. */
@Getter
public class UpdateAdmissionApplicantRequest {
    @NotNull
    @Min(0)
    private Long expectedRevision;

    private String applicationCode;
    private String citizenId;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String email;
    private String phone;
    private BigDecimal entranceScore;

    @Getter(lombok.AccessLevel.NONE)
    private final Set<String> providedFields = new HashSet<>();

    public void setExpectedRevision(Object revision) {
        if (revision != null && !(revision instanceof Integer) && !(revision instanceof Long)) {
            rejectUnknownField("expectedRevision", revision);
        }
        expectedRevision = revision == null ? null : ((Number) revision).longValue();
    }

    public void setApplicationCode(String applicationCode) {
        this.applicationCode = applicationCode;
        providedFields.add("applicationCode");
    }

    public void setCitizenId(String citizenId) {
        this.citizenId = citizenId;
        providedFields.add("citizenId");
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
        providedFields.add("fullName");
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
        providedFields.add("dateOfBirth");
    }

    public void setGender(String gender) {
        this.gender = gender;
        providedFields.add("gender");
    }

    public void setEmail(String email) {
        this.email = email;
        providedFields.add("email");
    }

    public void setPhone(String phone) {
        this.phone = phone;
        providedFields.add("phone");
    }

    public void setEntranceScore(BigDecimal entranceScore) {
        this.entranceScore = entranceScore;
        providedFields.add("entranceScore");
    }

    @JsonIgnore
    public boolean hasField(String fieldName) {
        return providedFields.contains(fieldName);
    }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object fieldValue) {
        throw new AdmissionApplicantException(
                ErrorCode.VALIDATION_ERROR,
                List.of(new FieldErrorDetail(fieldName, "Trường không được phép hoặc sai kiểu dữ liệu: " + fieldName)));
    }
}
