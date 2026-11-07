package com.eduhub.edu_hub.people.application.admission;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.people.api.dto.request.AdmissionRequests.PlanRequest;
import com.eduhub.edu_hub.people.api.dto.response.AdmissionResponses.PlanResponse;
import com.eduhub.edu_hub.people.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.repository.*;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.persistence.EntityManager;
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
    private final CohortRepository cohorts;
    private final CurriculumFacade.Majors majors;
    private final EntityManager entityManager;
    private final AdmissionSettings settings;
    private final AdmissionCapacityPolicy capacity;
    private final AdmissionView view;
    private final AdmissionImportService imports;
    private final AdmissionSpreadsheet spreadsheet;

    public byte[] template(Long majorId, Long cohortId) {
        var major = majors.requireMajor(majorId);
        var cohort = requireCohort(cohortId);
        return spreadsheet.template(
                new AdmissionSpreadsheet.Scope(major.majorCode(), major.majorName(), cohort.getCohortCode()));
    }

    @Transactional
    public PlanResponse createWithFile(PlanRequest request, MultipartFile file) {
        PlanResponse created = create(request);
        return imports.upload(created.id(), created.revision(), file);
    }

    @Transactional
    public PlanResponse create(PlanRequest request) {

        int minimum = request.minCapacity() == null ? settings.getMinCapacity() : request.minCapacity();
        int maximum = request.maxCapacity() == null ? settings.getMaxCapacity() : request.maxCapacity();
        int reserve = request.reserveCapacity() == null ? settings.getReserveCapacity() : request.reserveCapacity();

        // validate nghiệp vụ cho 3 giá trị: sức chứa tối đa, sức chứa tối thiểu, suất dự bị mỗi lớp
        capacity.validate(minimum, maximum, reserve);

        // gọi facade lôi majors lên
        majors.requireMajor(request.majorId());

        // lôi niên khóa lên
        var cohort = requireCohort(request.cohortId());

        // tạo entity
        AdmissionPlan plan = new AdmissionPlan();
        plan.setMajor(entityManager.getReference(Major.class, request.majorId()));
        plan.setCohort(cohort);
        plan.setTotalStudents(request.totalStudents());
        plan.setMinCapacity(minimum);
        plan.setMaxCapacity(maximum);
        plan.setReserveCapacity(reserve);

        // ép ghi xuống DB ngay
        plans.saveAndFlush(plan);

        // TODO(AUDIT_LOG): lưu lại người thực hiện và trạng thái cấu hình lúc đó.
        log.info("Created admission plan: planId={}, totalStudents={}", plan.getId(), plan.getTotalStudents());

        return view.response(plan);
    }

    public PlanResponse get(Long id) {
        return view.response(plans.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_PLAN_NOT_FOUND)));
    }

    private Cohort requireCohort(Long cohortId) {
        var cohort = cohorts.findByIdAndDeletedFalse(cohortId)
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_STUDENT_CODE_COHORT));
        if (cohort.getCodeSequence() == null || cohort.getCodeSequence() < 1 || cohort.getCodeSequence() > 99) {
            throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_COHORT);
        }
        return cohort;
    }
}
