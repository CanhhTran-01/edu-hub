package com.eduhub.edu_hub.admission.application.service;

import static com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey.*;

import com.eduhub.edu_hub.admission.api.dto.request.CreateAdmissionPlanFromFileRequest;
import com.eduhub.edu_hub.admission.api.dto.request.ImportAdmissionApplicantsRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionCapacityResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanLookupResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanResponse;
import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionCapacityPolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanRepository;
import com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetScope;
import com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetTemplateWriter;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.shared.exception.*;
import com.eduhub.edu_hub.shared.systemconfig.application.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdmissionPlanningService {
    private final AdmissionPlanRepository planRepository;
    private final PeopleFacade.Admission peopleAdmissionFacade;
    private final CurriculumFacade.Majors majorCatalogFacade;
    private final SystemConfigService systemConfigService;
    private final AdmissionCapacityPolicy capacityPolicy;
    private final AdmissionPlanQueryService planQueryService;
    private final AdmissionImportService importService;
    private final AdmissionSpreadsheetTemplateWriter templateWriter;

    public AdmissionCapacityResponse getDefaultCapacityConfiguration() {
        var configuration = systemConfigService.getSnapshot();
        return new AdmissionCapacityResponse(
                configuration.getInteger(ADMISSION_MINIMUM_CAPACITY),
                configuration.getInteger(ADMISSION_MAXIMUM_CAPACITY),
                configuration.getInteger(ADMISSION_RESERVE_CAPACITY));
    }

    public byte[] generateImportTemplate(Long majorId, Long cohortId) {
        var major = majorCatalogFacade.requireMajor(majorId);
        var cohort = peopleAdmissionFacade.requireCohort(cohortId);
        return templateWriter.writeTemplate(AdmissionSpreadsheetScope.builder()
                .majorCode(major.majorCode())
                .majorName(major.majorName())
                .cohortCode(cohort.cohortCode())
                .build());
    }

    @Transactional
    public AdmissionPlanResponse createPlanFromFile(CreateAdmissionPlanFromFileRequest request) {
        // 1. Chốt cấu hình sĩ số của phương án trước khi đọc và phân loại hồ sơ trong file.
        var configuration = systemConfigService.getSnapshot();
        int minCapacity = configuration.getInteger(ADMISSION_MINIMUM_CAPACITY);
        int maxCapacity = configuration.getInteger(ADMISSION_MAXIMUM_CAPACITY);
        int reserveCapacity = configuration.getInteger(ADMISSION_RESERVE_CAPACITY);
        capacityPolicy.validateCapacityLimits(minCapacity, maxCapacity, reserveCapacity);

        // 2. Ngành/khóa người dùng chọn là phạm vi chính thức; file chỉ cung cấp thông tin để đối chiếu.
        majorCatalogFacade.requireMajor(request.majorId());
        peopleAdmissionFacade.requireCohort(request.cohortId());

        // Cùng khóa ngành–khóa ở bước tạo; unique index bảo vệ thêm các đường ghi cạnh tranh khác.
        peopleAdmissionFacade.lockCohort(request.cohortId());
        if (planRepository.existsByMajorIdAndCohortIdAndDeletedFalse(request.majorId(), request.cohortId())) {
            throw new AdmissionPlanException(ErrorCode.ADMISSION_PLAN_ALREADY_EXISTS);
        }

        // 3. Lưu phương án để lấy ID gắn hồ sơ; tổng số sẽ được import cập nhật từ các hồ sơ thực tế.
        AdmissionPlan plan = AdmissionPlan.builder()
                .majorId(request.majorId())
                .cohortId(request.cohortId())
                .totalStudents(0)
                .minCapacity(minCapacity)
                .maxCapacity(maxCapacity)
                .reserveCapacity(reserveCapacity)
                .build();
        planRepository.saveAndFlush(plan);

        // 4. Import tham gia cùng transaction: lỗi file hoặc đối chiếu phạm vi phải rollback cả phương án mới.
        // Chỉ dựng response/kịch bản sau import, không trả phương án rỗng như một bước nghiệp vụ riêng.
        AdmissionPlanResponse importedPlan = importService.importApplicants(
                plan.getId(), new ImportAdmissionApplicantsRequest(plan.getRevision(), request.file()));
        // TODO(AUDIT_LOG): Ghi người lập phương án, ngành/khóa và cấu hình sĩ số sau khi commit.
        log.info(
                "Created admission plan from file: planId={}, totalStudents={}",
                importedPlan.id(),
                importedPlan.totalStudents());
        return importedPlan;
    }

    public AdmissionPlanResponse getPlan(Long planId) {
        return planQueryService.buildPlanResponse(planRepository
                .findByIdAndDeletedFalse(planId)
                .orElseThrow(() -> new AdmissionPlanException(ErrorCode.ADMISSION_PLAN_NOT_FOUND)));
    }

    /** Mở lại cùng phương án theo ngành–khóa; không tạo plan từ thao tác GET. */
    public AdmissionPlanLookupResponse findPlanByScope(Long majorId, Long cohortId) {
        if (majorId == null || majorId <= 0 || cohortId == null || cohortId <= 0) {
            throw new AdmissionPlanException(ErrorCode.VALIDATION_ERROR);
        }
        return new AdmissionPlanLookupResponse(
                majorId,
                cohortId,
                planRepository
                        .findByMajorIdAndCohortIdAndDeletedFalse(majorId, cohortId)
                        .map(planQueryService::buildPlanResponse)
                        .orElse(null));
    }
}
