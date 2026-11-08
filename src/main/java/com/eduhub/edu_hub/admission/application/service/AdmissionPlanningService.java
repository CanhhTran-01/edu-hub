package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.api.dto.request.CreateAdmissionPlanFromFileRequest;
import com.eduhub.edu_hub.admission.api.dto.request.CreateAdmissionPlanRequest;
import com.eduhub.edu_hub.admission.api.dto.request.ImportAdmissionApplicantsRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionCapacityResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanResponse;
import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionCapacityPolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanRepository;
import com.eduhub.edu_hub.admission.infrastructure.config.AdmissionSettings;
import com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetScope;
import com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetTemplateWriter;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.shared.exception.*;
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
    private final AdmissionSettings admissionSettings;
    private final AdmissionCapacityPolicy capacityPolicy;
    private final AdmissionPlanQueryService planQueryService;
    private final AdmissionImportService importService;
    private final AdmissionSpreadsheetTemplateWriter templateWriter;

    public AdmissionCapacityResponse getDefaultCapacityConfiguration() {
        return new AdmissionCapacityResponse(
                admissionSettings.getMinCapacity(),
                admissionSettings.getMaxCapacity(),
                admissionSettings.getReserveCapacity());
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
        AdmissionPlanResponse createdPlan = createPlan(new CreateAdmissionPlanRequest(
                request.majorId(),
                request.cohortId(),
                0,
                request.minCapacity(),
                request.maxCapacity(),
                request.reserveCapacity()));
        return importService.importApplicants(
                createdPlan.id(), new ImportAdmissionApplicantsRequest(createdPlan.revision(), request.file()));
    }

    @Transactional
    public AdmissionPlanResponse createPlan(CreateAdmissionPlanRequest request) {
        int minCapacity = request.minCapacity() == null ? admissionSettings.getMinCapacity() : request.minCapacity();
        int maxCapacity = request.maxCapacity() == null ? admissionSettings.getMaxCapacity() : request.maxCapacity();
        int reserveCapacity =
                request.reserveCapacity() == null ? admissionSettings.getReserveCapacity() : request.reserveCapacity();
        capacityPolicy.validateCapacityLimits(minCapacity, maxCapacity, reserveCapacity);
        majorCatalogFacade.requireMajor(request.majorId());
        peopleAdmissionFacade.requireCohort(request.cohortId());
        AdmissionPlan plan = AdmissionPlan.builder()
                .majorId(request.majorId())
                .cohortId(request.cohortId())
                .totalStudents(request.totalStudents())
                .minCapacity(minCapacity)
                .maxCapacity(maxCapacity)
                .reserveCapacity(reserveCapacity)
                .build();
        planRepository.saveAndFlush(plan);
        // TODO(AUDIT_LOG): Ghi người lập phương án, ngành/khóa và cấu hình sĩ số sau khi commit.
        log.info("Created admission plan: planId={}, totalStudents={}", plan.getId(), plan.getTotalStudents());
        return planQueryService.buildPlanResponse(plan);
    }

    public AdmissionPlanResponse getPlan(Long planId) {
        return planQueryService.buildPlanResponse(planRepository
                .findByIdAndDeletedFalse(planId)
                .orElseThrow(() -> new AdmissionPlanException(ErrorCode.ADMISSION_PLAN_NOT_FOUND)));
    }
}
