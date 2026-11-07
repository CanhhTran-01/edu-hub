package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.PlanCommand;
import com.eduhub.edu_hub.admission.application.command.AdmissionImportScope;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.PlanResult;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionCapacityPolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanRepository;
import com.eduhub.edu_hub.admission.infrastructure.config.AdmissionSettings;
import com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionTemplateWriter;
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
    private final AdmissionPlanRepository plans;
    private final PeopleFacade.Admission people;
    private final CurriculumFacade.Majors majors;
    private final AdmissionSettings settings;
    private final AdmissionCapacityPolicy capacity;
    private final AdmissionPlanQueryService view;
    private final AdmissionImportService imports;
    private final AdmissionTemplateWriter spreadsheet;

    public byte[] template(Long majorId, Long cohortId) {
        var major = majors.requireMajor(majorId);
        var cohort = people.requireCohort(cohortId);
        return spreadsheet.write(AdmissionImportScope.builder()
                .majorCode(major.majorCode())
                .majorName(major.majorName())
                .cohortCode(cohort.cohortCode())
                .build());
    }

    @Transactional
    public PlanResult createWithFile(PlanCommand request, MultipartFile file) {
        PlanResult created = create(request);
        return imports.upload(created.id(), created.revision(), file);
    }

    @Transactional
    public PlanResult create(PlanCommand request) {
        int minimum = request.minCapacity() == null ? settings.getMinCapacity() : request.minCapacity();
        int maximum = request.maxCapacity() == null ? settings.getMaxCapacity() : request.maxCapacity();
        int reserve = request.reserveCapacity() == null ? settings.getReserveCapacity() : request.reserveCapacity();
        capacity.validate(minimum, maximum, reserve);
        majors.requireMajor(request.majorId());
        people.requireCohort(request.cohortId());
        AdmissionPlan plan = AdmissionPlan.builder()
                .majorId(request.majorId())
                .cohortId(request.cohortId())
                .totalStudents(request.totalStudents())
                .minCapacity(minimum)
                .maxCapacity(maximum)
                .reserveCapacity(reserve)
                .build();
        plans.saveAndFlush(plan);
        // TODO(AUDIT_LOG): Ghi người lập phương án, ngành/khóa và cấu hình sĩ số sau khi commit.
        log.info("Created admission plan: planId={}, totalStudents={}", plan.getId(), plan.getTotalStudents());
        return view.response(plan);
    }

    public PlanResult get(Long id) {
        return view.response(plans.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_PLAN_NOT_FOUND)));
    }
}
