package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.api.dto.request.ImportAdmissionApplicantsRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanResponse;
import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.application.mapper.AdmissionApplicantImportMapper;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionApplicantMergePolicy;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionApplicantValidationPolicy;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionPlanPolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionApplicantRepository;
import com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetReader;
import com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetScope;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdmissionImportService {
    private final AdmissionPlanLockService planLockService;
    private final AdmissionApplicantRepository applicantRepository;
    private final PeopleFacade.Admission peopleAdmissionFacade;
    private final CurriculumFacade.Majors majorCatalogFacade;
    private final AdmissionSpreadsheetReader spreadsheetReader;
    private final AdmissionApplicantClassificationService applicantClassificationService;
    private final AdmissionApplicantValidationPolicy applicantValidationPolicy;
    private final AdmissionApplicantMergePolicy applicantMergePolicy;
    private final AdmissionApplicantImportMapper applicantMapper;
    private final AdmissionPlanQueryService planQueryService;
    private final Clock clock;
    private final AdmissionAllocationService allocationService;

    @Transactional
    public AdmissionPlanResponse importApplicants(Long planId, ImportAdmissionApplicantsRequest request) {

        // lấy plan và khóa lại: pessimistic lock và optimistic lock thủ công
        AdmissionPlan plan = planLockService.lockPlanAtRevision(planId, request.expectedRevision());

        // đang tạo sinh viên thì không cho upload lại
        if (plan.getState() == AdmissionPlanStatus.CONFIRMING) {
            throw new AdmissionPlanException(ErrorCode.ADMISSION_INVALID_STATE);
        }

        List<AdmissionApplicant> importedApplicants = readApplicants(plan, request.file());

        // Lấy dòng cũ của plan, kiểm tra có ai nhập học thành công (ENROLLED) chưa
        List<AdmissionApplicant> existingApplicants =
                applicantRepository.findByPlan_IdAndDeletedFalseOrderByRowNumber(planId);
        validateApplicants(plan, importedApplicants);
        applicantClassificationService.classifyApplicants(importedApplicants);

        // File bổ sung không thay thế hoặc cập nhật dòng cũ; đối chiếu CCCD cả với người đã ENROLLED.
        int maximumStoredRowNumber = applicantRepository.findMaximumRowNumberByPlanIdIncludingDeleted(planId);
        importedApplicants =
                applicantMergePolicy.appendApplicants(existingApplicants, importedApplicants, maximumStoredRowNumber);
        applicantRepository.saveAll(importedApplicants);
        applicantRepository.flush();
        allocationService.prepareAndAllocateApplicants(plan, importedApplicants);

        // đánh dấu kế hoạch thay đổi
        AdmissionPlanPolicy.incrementRevision(plan);

        // TODO(AUDIT_LOG): Ghi lần import, hồ sơ bị loại/xung đột và quyết định xử lý lại sau commit.
        log.info(
                "Imported admission candidates: planId={}, rows={}, eligible={}",
                planId,
                importedApplicants.size(),
                plan.getTotalStudents());

        return planQueryService.buildPlanResponse(plan);
    }

    private List<AdmissionApplicant> readApplicants(AdmissionPlan plan, MultipartFile file) {
        // Ngành/khóa của plan là nguồn chính thức. File chỉ cung cấp thông tin đối chiếu,
        // không được đổi ngành/khóa của plan hoặc quyết định tiền tố lớp/MSSV.
        // Kiểm tra trước mọi thao tác xóa, thay thế hoặc gộp danh sách đã nhập.
        var major = majorCatalogFacade.requireMajor(plan.getMajorId());
        var cohort = peopleAdmissionFacade.requireCohort(plan.getCohortId());
        return new ArrayList<>(spreadsheetReader
                .parse(
                        file,
                        AdmissionSpreadsheetScope.builder()
                                .majorCode(major.majorCode())
                                .majorName(major.majorName())
                                .cohortCode(cohort.cohortCode())
                                .build())
                .stream()
                .map(applicantMapper::fromSpreadsheetApplicant)
                .toList());
    }

    private void validateApplicants(AdmissionPlan plan, List<AdmissionApplicant> importedApplicants) {
        // validate các AdmissionApplicant đã parse từ file mới
        // Chốt cùng ngày Việt Nam cho toàn bộ file để không đổi kết quả giữa các dòng khi qua nửa đêm.
        LocalDate validationDate = LocalDate.now(clock);
        for (AdmissionApplicant applicant : importedApplicants) {

            applicant.setPlan(plan); // applicant này thuộc về plan hiện tại

            if (applicant.getState() == AdmissionApplicantStatus.INVALID) continue;

            List<String> validationErrors = applicantValidationPolicy.validateApplicant(applicant, validationDate);
            if (!validationErrors.isEmpty())
                setApplicantStatus(applicant, AdmissionApplicantStatus.INVALID, String.join("; ", validationErrors));
        }
    }

    private void setApplicantStatus(
            AdmissionApplicant applicant, AdmissionApplicantStatus status, String errorMessage) {
        applicant.setState(status);
        applicant.setErrorMessage(errorMessage);
    }
}
