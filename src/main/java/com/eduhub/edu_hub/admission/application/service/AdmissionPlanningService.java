package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.CreatePlanCommand;
import com.eduhub.edu_hub.admission.application.command.AdmissionImportScope;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.PlanResult;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionCapacityPolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanRepository;
import com.eduhub.edu_hub.admission.infrastructure.config.AdmissionSettings;
import com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetTemplateWriter;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.shared.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

    public byte[] generateImportTemplate(Long majorId, Long cohortId) {
        var major = majorCatalogFacade.requireMajor(majorId);
        var cohort = peopleAdmissionFacade.requireCohort(cohortId);
        return templateWriter.writeTemplate(AdmissionImportScope.builder()
                .majorCode(major.majorCode())
                .majorName(major.majorName())
                .cohortCode(cohort.cohortCode())
                .build());
    }

    @Transactional
    public PlanResult createPlanFromFile(CreatePlanCommand command, MultipartFile file) {
        PlanResult createdPlan = createPlan(command);
        return importService.importApplicants(createdPlan.id(), createdPlan.revision(), file);
    }

    @Transactional
    public PlanResult createPlan(CreatePlanCommand command) {
        int minCapacity = command.minCapacity() == null ? admissionSettings.getMinCapacity() : command.minCapacity();
        int maxCapacity = command.maxCapacity() == null ? admissionSettings.getMaxCapacity() : command.maxCapacity();
        int reserveCapacity =
                command.reserveCapacity() == null ? admissionSettings.getReserveCapacity() : command.reserveCapacity();
        capacityPolicy.validateCapacityLimits(minCapacity, maxCapacity, reserveCapacity);
        majorCatalogFacade.requireMajor(command.majorId());
        peopleAdmissionFacade.requireCohort(command.cohortId());
        AdmissionPlan plan = AdmissionPlan.builder()
                .majorId(command.majorId())
                .cohortId(command.cohortId())
                .totalStudents(command.totalStudents())
                .minCapacity(minCapacity)
                .maxCapacity(maxCapacity)
                .reserveCapacity(reserveCapacity)
                .build();
        planRepository.saveAndFlush(plan);
        // TODO(AUDIT_LOG): Ghi người lập phương án, ngành/khóa và cấu hình sĩ số sau khi commit.
        log.info("Created admission plan: planId={}, totalStudents={}", plan.getId(), plan.getTotalStudents());
        return planQueryService.buildPlanResult(plan);
    }

    public PlanResult getPlan(Long planId) {
        return planQueryService.buildPlanResult(planRepository
                .findByIdAndDeletedFalse(planId)
                .orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_PLAN_NOT_FOUND)));
    }
}
