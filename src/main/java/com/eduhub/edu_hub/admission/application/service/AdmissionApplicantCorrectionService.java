package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.api.dto.request.DeleteAdmissionApplicantRequest;
import com.eduhub.edu_hub.admission.api.dto.request.UpdateAdmissionApplicantRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanResponse;
import com.eduhub.edu_hub.admission.application.exception.*;
import com.eduhub.edu_hub.admission.domain.enums.*;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.policy.*;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionApplicantRepository;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.common.TextsNormalizer;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sửa hoặc bỏ dòng lỗi theo applicantId; danh tính Student/User chính thức và ENROLLED được bảo toàn. */
@Service
@RequiredArgsConstructor
public class AdmissionApplicantCorrectionService {
    private final AdmissionPlanLockService planLockService;
    private final AdmissionApplicantRepository applicantRepository;
    private final AdmissionApplicantValidationPolicy validationPolicy;
    private final AdmissionApplicantClassificationService classificationService;
    private final AdmissionIdentityPolicy identityPolicy;
    private final AdmissionAllocationService allocationService;
    private final AdmissionPlanQueryService planQueryService;
    private final Clock clock;

    @Transactional
    public AdmissionPlanResponse updateApplicant(
            Long planId, Long applicantId, UpdateAdmissionApplicantRequest request) {
        var plan = lockPlanForApplicantCorrection(planId, request.getExpectedRevision());
        var applicants = applicantRepository.findByPlan_IdAndDeletedFalseOrderByRowNumber(planId);
        var applicant = findApplicantForCorrection(applicants, applicantId);
        // Kiểm tra bản dự kiến trước khi sửa instance JPA đang quản lý; field không gửi giữ nguyên.
        var proposedApplicant = AdmissionApplicant.builder().build();
        BeanUtils.copyProperties(applicant, proposedApplicant);
        applyRequestedFields(proposedApplicant, request);
        var validationErrors = validationPolicy.validateApplicant(proposedApplicant, LocalDate.now(clock));
        if (!validationErrors.isEmpty()) {
            throw new AdmissionApplicantException(
                    ErrorCode.VALIDATION_ERROR,
                    validationErrors.stream()
                            .map(message -> new FieldErrorDetail("applicant", message))
                            .toList());
        }
        BeanUtils.copyProperties(
                proposedApplicant,
                applicant,
                "id",
                "plan",
                "rowNumber",
                "createdAt",
                "createdBy",
                "updatedAt",
                "updatedBy",
                "deleted");
        // Chỉ thao tác sửa tường minh mới gỡ lỗi parse của dòng đích. Các dòng INVALID khác giữ nguyên.
        applicant.setState(AdmissionApplicantStatus.VALID);
        reclassifyApplicants(applicants);
        allocationService.prepareAndAllocateApplicants(plan, applicants);
        AdmissionPlanPolicy.incrementRevision(plan);
        applicantRepository.flush();
        // TODO(AUDIT_LOG): Ghi các trường được sửa và kết quả phân loại lại sau commit; không ghi CCCD đầy đủ.
        return planQueryService.buildPlanResponse(plan);
    }

    /** Bỏ dòng lỗi khỏi tập đang xử lý; giữ bản ghi gốc và không tác động hồ sơ nhập học chính thức. */
    @Transactional
    public AdmissionPlanResponse deleteApplicant(
            Long planId, Long applicantId, DeleteAdmissionApplicantRequest request) {
        var plan = lockPlanForApplicantCorrection(planId, request.expectedRevision());
        var applicants = applicantRepository.findByPlan_IdAndDeletedFalseOrderByRowNumber(planId);
        var applicant = findApplicantForCorrection(applicants, applicantId);
        applicant.setDeleted(true);
        var remainingApplicants =
                applicants.stream().filter(candidate -> !candidate.isDeleted()).toList();
        // INVALID/CONFLICT vốn không tính sĩ số; bỏ một dòng có thể gỡ xung đột và làm dòng khác đủ điều kiện.
        reclassifyApplicants(remainingApplicants);
        allocationService.prepareAndAllocateApplicants(plan, remainingApplicants);
        AdmissionPlanPolicy.incrementRevision(plan);
        applicantRepository.flush();
        // TODO(AUDIT_LOG): Ghi người bỏ hồ sơ, applicantId và kết quả đối chiếu sau commit; không ghi CCCD đầy đủ.
        return planQueryService.buildPlanResponse(plan);
    }

    private AdmissionPlan lockPlanForApplicantCorrection(Long planId, Long expectedRevision) {
        if (expectedRevision == null || expectedRevision < 0)
            throw new AdmissionPlanException(ErrorCode.VALIDATION_ERROR);
        var plan = planLockService.lockPlanAtRevision(planId, expectedRevision);
        if (plan.getState() == AdmissionPlanStatus.CONFIRMING)
            throw new AdmissionPlanException(ErrorCode.ADMISSION_INVALID_STATE);
        return plan;
    }

    private AdmissionApplicant findApplicantForCorrection(List<AdmissionApplicant> applicants, Long applicantId) {
        var applicant = applicants.stream()
                .filter(candidate -> !candidate.isDeleted() && Objects.equals(candidate.getId(), applicantId))
                .findFirst()
                .orElseThrow(() -> new AdmissionPlanException(ErrorCode.ADMISSION_APPLICANT_NOT_FOUND));
        if (applicant.getState() != AdmissionApplicantStatus.INVALID
                && applicant.getState() != AdmissionApplicantStatus.CONFLICT)
            throw new AdmissionPlanException(ErrorCode.ADMISSION_INVALID_STATE);
        return applicant;
    }

    private void reclassifyApplicants(List<AdmissionApplicant> applicants) {
        Map<AdmissionApplicant, String> previousFailures = new IdentityHashMap<>();
        for (var candidate : applicants) {
            if (candidate.getState() == AdmissionApplicantStatus.ENROLLED
                    || candidate.getState() == AdmissionApplicantStatus.INVALID) continue;
            if (candidate.getState() == AdmissionApplicantStatus.FAILED)
                previousFailures.put(candidate, candidate.getErrorMessage());
            candidate.setState(AdmissionApplicantStatus.VALID);
            candidate.setErrorMessage(null);
            candidate.setStudentId(null);
            candidate.setReuseAccount(false);
            candidate.setWarningMessage(null);
        }
        // Đối chiếu lại cả các nhóm liên quan để một dòng được sửa có thể gỡ lỗi cho các dòng cùng mã/CCCD.
        classificationService.classifyApplicants(applicants);
        identityPolicy.markEnrolledApplicantConflicts(applicants);
        previousFailures.forEach((candidate, failure) -> {
            if (candidate.getState() == AdmissionApplicantStatus.VALID) {
                candidate.setState(AdmissionApplicantStatus.FAILED);
                candidate.setErrorMessage(failure);
            }
        });
    }

    private void applyRequestedFields(AdmissionApplicant applicant, UpdateAdmissionApplicantRequest request) {
        if (request.hasField("applicationCode"))
            applicant.setApplicationCode(TextsNormalizer.normalizeKey(request.getApplicationCode()));
        if (request.hasField("citizenId"))
            applicant.setCitizenId(TextsNormalizer.normalizeCitizenId(request.getCitizenId()));
        if (request.hasField("fullName"))
            applicant.setFullName(TextsNormalizer.normalizeOptionalText(request.getFullName()));
        if (request.hasField("dateOfBirth")) applicant.setDateOfBirth(request.getDateOfBirth());
        if (request.hasField("gender")) applicant.setGender(TextsNormalizer.normalizeOptionalText(request.getGender()));
        if (request.hasField("email")) applicant.setEmail(TextsNormalizer.normalizeEmail(request.getEmail()));
        if (request.hasField("phone")) applicant.setPhone(TextsNormalizer.normalizeOptionalText(request.getPhone()));
        if (request.hasField("entranceScore")) applicant.setEntranceScore(request.getEntranceScore());
    }
}
