package com.eduhub.edu_hub.people.application.service;

import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.domain.enums.AccessTokenPurpose;
import com.eduhub.edu_hub.people.api.dto.request.CreateCohortRequest;
import com.eduhub.edu_hub.people.api.dto.response.CohortResponse;
import com.eduhub.edu_hub.people.application.exception.CohortException;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.policy.CohortCreationPolicy;
import com.eduhub.edu_hub.people.domain.repository.CohortRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CohortApplicationService {
    private final CohortRepository cohortRepository;
    private final IdentityFacade identityFacade;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CohortResponse createCohort(Jwt principal, CreateCohortRequest request) {
        if (principal == null) throw new CohortException(ErrorCode.UNAUTHENTICATED);
        if (!AccessTokenPurpose.ACCESS.name().equals(principal.getClaim(AccessTokenPurpose.CLAIM_NAME)))
            throw new CohortException(ErrorCode.FIRST_LOGIN_REQUIRED);
        var currentUser = identityFacade.requireCurrentUser(principal);
        // TODO(AUTHORIZATION): Giới hạn tạo khóa cho ACADEMIC_ADMIN khi triển khai phân quyền role chung.
        if (request == null) throw new CohortException(ErrorCode.VALIDATION_ERROR);
        CohortCreationPolicy.validateYears(request.getStartYear(), request.getEndYear());

        // Khóa bảng ngắn để cấp số tuần tự kể cả khi chưa có dòng nào; không khóa riêng dòng MAX rồi bỏ sót khóa mới.
        // Cả kiểm tra năm, cấp mã và lưu khóa cùng transaction; rollback không để lại khóa được tạo một phần.
        cohortRepository.lockForCohortCreation();
        if (cohortRepository.existsByStartYear(request.getStartYear()))
            throw new CohortException(
                    ErrorCode.COHORT_ALREADY_EXISTS, "startYear", "Năm bắt đầu đã có khóa tuyển sinh");
        int lastIssuedSequence = cohortRepository.findLastIssuedCodeSequence();
        if (lastIssuedSequence >= CohortCreationPolicy.MAXIMUM_COHORT_SEQUENCE)
            throw new CohortException(ErrorCode.COHORT_CODE_SEQUENCE_EXCEEDED);
        int cohortSequence = lastIssuedSequence + 1;
        String cohortCode = CohortCreationPolicy.generateCohortCode(cohortSequence);
        if (cohortRepository.existsByNormalizedCohortCode(cohortCode))
            throw new CohortException(
                    ErrorCode.COHORT_ALREADY_EXISTS,
                    "cohortCode",
                    "Mã khóa đã tồn tại; cần đối soát số thứ tự của khóa lịch sử trước khi cấp mới");

        Cohort cohort = new Cohort();
        cohort.setCohortCode(cohortCode);
        cohort.setCodeSequence(cohortSequence);
        cohort.setStartYear(request.getStartYear());
        cohort.setEndYear(request.getEndYear());
        cohort.setCreatedBy(currentUser.getId().toString());
        cohort.setUpdatedBy(currentUser.getId().toString());
        Cohort savedCohort = cohortRepository.saveAndFlush(cohort);
        // TODO(AUDIT_LOG): Ghi actor, khóa, niên khóa và mã được cấp sau commit khi có hạ tầng audit.
        return new CohortResponse(
                savedCohort.getId(), savedCohort.getCohortCode(), savedCohort.getStartYear(), savedCohort.getEndYear());
    }

    public List<CohortResponse> findCohorts() {
        // Giữ khóa cũ và năm chưa khai báo để mở lại phương án; điều kiện nhập học được kiểm tra khi thao tác.
        return cohortRepository.findAllByDeletedFalseOrderByIdDesc().stream()
                .map(cohort -> new CohortResponse(
                        cohort.getId(), cohort.getCohortCode(), cohort.getStartYear(), cohort.getEndYear()))
                .toList();
    }
}
