package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.api.dto.request.ConfirmAdmissionEnrollmentRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionConfirmationResponse;
import com.eduhub.edu_hub.admission.application.exception.AdmissionEnrollmentException;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlanClass;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionPlanPolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionApplicantRepository;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanClassRepository;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanRepository;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.shared.common.TextsNormalizer;
import com.eduhub.edu_hub.shared.exception.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class AdmissionConfirmationService {
    private final AdmissionPlanRepository planRepository;
    private final AdmissionPlanClassRepository planClassRepository;
    private final AdmissionApplicantRepository applicantRepository;
    private final PeopleFacade.Admission peopleAdmissionFacade;
    private final AdmissionPlanQueryService planQueryService;
    private final AdmissionApplicantEnrollmentService enrollmentService;
    private final AdmissionConfirmationLeaseService confirmationLease;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public AdmissionConfirmationResponse confirmEnrollment(Long planId, ConfirmAdmissionEnrollmentRequest request) {
        // 1. Chuẩn bị phiên xác nhận, kiểm tra tính hợp lệ và lấy danh sách thí sinh cần nhập học.
        ConfirmationBatch confirmationBatch = executeInTransaction(() -> prepareConfirmationBatch(planId, request));

        // 2. Mỗi hồ sơ một transaction: một người lỗi không rollback những người đã nhập học thành công.
        for (Long applicantId : confirmationBatch.applicantIds()) {
            try {
                executeInTransaction(() -> {
                    enrollmentService.enroll(planId, confirmationBatch.confirmationToken(), applicantId);
                    return true;
                });
            } catch (RuntimeException exception) {
                // 3. Dừng toàn bộ tiến trình nếu trạng thái xác nhận không còn hợp lệ.
                if (exception instanceof BaseException base
                        && base.getErrorCode() == ErrorCode.ADMISSION_INVALID_STATE) {
                    throw exception;
                }

                // 4. Sử dụng transaction riêng để ghi nhận hồ sơ thất bại.
                executeInTransaction(() -> {
                    var plan =
                            confirmationLease.requireActiveConfirmation(planId, confirmationBatch.confirmationToken());
                    var applicant = applicantRepository
                            .findByIdAndDeletedFalse(applicantId)
                            .orElseThrow(
                                    () -> new AdmissionEnrollmentException(ErrorCode.ADMISSION_APPLICANT_NOT_FOUND));

                    // Chỉ đánh dấu thất bại đối với hồ sơ vẫn đang chờ nhập học.
                    if (applicant.isPendingEnrollment()) {
                        applicant.setState(AdmissionApplicantStatus.FAILED);
                        applicant.setErrorMessage(
                                exception instanceof BaseException base
                                        ? base.getErrorCode().name()
                                        : "Không thể lưu hồ sơ; có thể thử lại");
                    }

                    // Gia hạn lease để tiếp tục xử lý các hồ sơ còn lại.
                    confirmationLease.extendConfirmationLease(plan);
                    return true;
                });
                log.warn(
                        "Admission row failed: planId={}, applicantId={}, failureType={}",
                        planId,
                        applicantId,
                        exception.getClass().getSimpleName());
            }
        }

        // 5. Tổng hợp kết quả, cập nhật trạng thái kế hoạch và kết thúc phiên xác nhận trong transaction riêng.
        return executeInTransaction(
                () -> completeConfirmationBatch(planId, confirmationBatch.confirmationToken(), request));
    }

    private ConfirmationBatch prepareConfirmationBatch(Long planId, ConfirmAdmissionEnrollmentRequest request) {
        // 1. Khóa kế hoạch để tránh các yêu cầu xác nhận đồng thời.
        var plan = planRepository
                .findForUpdate(planId)
                .orElseThrow(() -> new AdmissionEnrollmentException(ErrorCode.ADMISSION_PLAN_NOT_FOUND));

        // 2. Đảm bảo tính idempotent và kiểm tra phiên bản kế hoạch.
        if (plan.getState() == AdmissionPlanStatus.CONFIRMED) return new ConfirmationBatch(null, List.of());
        if (plan.getRevision() != request.expectedRevision())
            throw new AdmissionEnrollmentException(ErrorCode.ADMISSION_STALE_PLAN);

        // 3. Kiểm tra trạng thái hợp lệ và cho phép khôi phục tiến trình xác nhận khi lease hết hạn.
        boolean confirmationLeaseExpired = plan.getState() == AdmissionPlanStatus.CONFIRMING
                && plan.getProcessingUntil() != null
                && !plan.getProcessingUntil().isAfter(LocalDateTime.now(clock));
        if (plan.getState() != AdmissionPlanStatus.PREVIEWED
                && plan.getState() != AdmissionPlanStatus.PARTIALLY_CONFIRMED
                && !confirmationLeaseExpired) throw new AdmissionEnrollmentException(ErrorCode.ADMISSION_INVALID_STATE);

        // 4. Lấy dữ liệu kế hoạch và kiểm tra sĩ số dự kiến của các lớp trước khi nhập học.
        var planApplicants = applicantRepository.findByPlan_IdAndDeletedFalseOrderByRowNumber(planId);
        var plannedClasses = planClassRepository.findByPlan_IdOrderByPosition(planId);
        if (plannedClasses.isEmpty() || planApplicants.stream().noneMatch(AdmissionApplicant::isIncludedInPlan))
            throw new AdmissionEnrollmentException(ErrorCode.ADMISSION_INVALID_STATE);
        validateProjectedClassSizes(plan, planApplicants, plannedClasses, request);

        // 5. Lấy danh sách thí sinh chưa nhập học, sắp xếp theo thứ tự lớp, họ tên và mã hồ sơ.
        Map<Long, Integer> classPositionsById = new HashMap<>();
        plannedClasses.forEach(plannedClass ->
                classPositionsById.put(plannedClass.getAdministrativeClassId(), plannedClass.getPosition()));
        var pendingApplicantIds = planApplicants.stream()
                .filter(AdmissionApplicant::isPendingEnrollment)
                .sorted(Comparator.comparingInt((AdmissionApplicant applicant) ->
                                classPositionsById.get(applicant.getAdministrativeClassId()))
                        .thenComparing(AdmissionApplicant::getFullName, TextsNormalizer.vietnameseNameOrder())
                        .thenComparing(AdmissionApplicant::getApplicationCode))
                .map(AdmissionApplicant::getId)
                .toList();

        // 6. Khởi tạo phiên xác nhận mới, cập nhật trạng thái, tăng revision và gia hạn lease.
        String confirmationToken = UUID.randomUUID().toString();
        plan.setConfirmationToken(confirmationToken);
        plan.setState(AdmissionPlanStatus.CONFIRMING);
        plan.setExceptionReason(TextsNormalizer.normalizeOptionalText(request.reason()));
        AdmissionPlanPolicy.incrementRevision(plan);
        confirmationLease.extendConfirmationLease(plan);
        return new ConfirmationBatch(confirmationToken, pendingApplicantIds);
    }

    private void validateProjectedClassSizes(
            AdmissionPlan plan,
            List<AdmissionApplicant> planApplicants,
            List<AdmissionPlanClass> plannedClasses,
            ConfirmAdmissionEnrollmentRequest request) {
        // 1. Đảm bảo mọi thí sinh thuộc kế hoạch đều được phân vào lớp hợp lệ.
        var allowedClassIds = plannedClasses.stream()
                .map(AdmissionPlanClass::getAdministrativeClassId)
                .collect(java.util.stream.Collectors.toSet());
        if (planApplicants.stream()
                .filter(AdmissionApplicant::isIncludedInPlan)
                .anyMatch(applicant -> applicant.getAdministrativeClassId() == null
                        || !allowedClassIds.contains(applicant.getAdministrativeClassId())))
            throw new AdmissionEnrollmentException(ErrorCode.ADMISSION_INVALID_STATE);

        // 2. Khóa các lớp theo thứ tự ID cố định để giảm nguy cơ deadlock.
        for (var plannedClass : plannedClasses.stream()
                .sorted(Comparator.comparing(AdmissionPlanClass::getAdministrativeClassId))
                .toList()) {
            var administrativeClass = peopleAdmissionFacade.lockClass(plannedClass.getAdministrativeClassId());

            // 3. Kiểm tra dữ liệu lớp có thay đổi so với cấu hình của kế hoạch hay không.
            if (administrativeClass.isDeleted()
                    || administrativeClass.getMaxCapacity() != plan.getMaxCapacity()
                    || administrativeClass.getReserveCapacity() != plan.getReserveCapacity()
                    || !administrativeClass.getMajorId().equals(plan.getMajorId())
                    || !administrativeClass.getCohortId().equals(plan.getCohortId()))
                throw new AdmissionEnrollmentException(ErrorCode.ADMISSION_DATA_CHANGED);

            // 4. Tính sĩ số dự kiến và số lượng thí sinh đã nhập học của từng lớp.
            long projectedClassSize = planApplicants.stream()
                    .filter(AdmissionApplicant::isIncludedInPlan)
                    .filter(applicant -> administrativeClass.getId().equals(applicant.getAdministrativeClassId()))
                    .count();
            long enrolledCount = planApplicants.stream()
                    .filter(applicant -> applicant.getState() == AdmissionApplicantStatus.ENROLLED)
                    .filter(applicant -> administrativeClass.getId().equals(applicant.getAdministrativeClassId()))
                    .count();

            // 5. Đảm bảo sĩ số thực tế khớp với số lượng thí sinh đã nhập học trong kế hoạch.
            if (administrativeClass.getCurrentSize() != enrolledCount)
                throw new AdmissionEnrollmentException(ErrorCode.ADMISSION_DATA_CHANGED);

            // 6. Không cho phép sĩ số dự kiến vượt quá sức chứa tối đa, kể cả chỉ tiêu dự phòng.
            if (projectedClassSize > plan.getMaxCapacity() + plan.getReserveCapacity())
                throw new AdmissionEnrollmentException(ErrorCode.CLASS_CAPACITY_EXCEEDED);

            // 7. Yêu cầu xác nhận khi sử dụng chỉ tiêu dự phòng hoặc khi sĩ số dưới mức tối thiểu.
            // Trường hợp dưới mức tối thiểu phải cung cấp thêm lý do.
            if ((projectedClassSize > plan.getMaxCapacity() && !request.acceptReserve())
                    || (projectedClassSize < plan.getMinCapacity()
                            && (!request.acceptBelowMinimum()
                                    || TextsNormalizer.normalizeOptionalText(request.reason()) == null)))
                throw new AdmissionEnrollmentException(ErrorCode.ADMISSION_WARNING_CONFIRMATION_REQUIRED);
        }
    }

    private AdmissionConfirmationResponse completeConfirmationBatch(
            Long planId, String confirmationToken, ConfirmAdmissionEnrollmentRequest request) {
        // 1. Khóa kế hoạch và lấy danh sách thí sinh để tổng hợp kết quả xác nhận.
        var plan = planRepository.findForUpdate(planId).orElseThrow();
        var planApplicants = applicantRepository.findByPlan_IdAndDeletedFalseOrderByRowNumber(planId);
        if (confirmationToken != null) {
            // 2. Đảm bảo phiên xác nhận hiện tại vẫn còn hiệu lực và đúng token.
            confirmationLease.requireActiveConfirmation(plan, confirmationToken);

            // 3. Kiểm tra thí sinh chưa hoàn tất nhập học và các lớp dưới sĩ số tối thiểu chưa được chấp thuận.
            boolean hasPendingApplicants = planApplicants.stream().anyMatch(AdmissionApplicant::isPendingEnrollment);
            boolean hasUnderfilledClasses = !request.acceptBelowMinimum()
                    && planClassRepository.findByPlan_IdOrderByPosition(planId).stream()
                            .anyMatch(plannedClass -> planApplicants.stream()
                                            .filter(AdmissionApplicant::isIncludedInPlan)
                                            .filter(applicant -> plannedClass
                                                    .getAdministrativeClassId()
                                                    .equals(applicant.getAdministrativeClassId()))
                                            .count()
                                    < plan.getMinCapacity());

            // 4. Xác định trạng thái cuối cùng: xác nhận một phần hoặc xác nhận hoàn tất.
            plan.setState(
                    hasPendingApplicants || hasUnderfilledClasses
                            ? AdmissionPlanStatus.PARTIALLY_CONFIRMED
                            : AdmissionPlanStatus.CONFIRMED);

            // 5. Cập nhật tổng số thí sinh thuộc kế hoạch, giải phóng phiên xác nhận và tăng revision.
            plan.setTotalStudents((int) planApplicants.stream()
                    .filter(AdmissionApplicant::isIncludedInPlan)
                    .count());
            plan.setConfirmationToken(null);
            plan.setProcessingUntil(null);
            AdmissionPlanPolicy.incrementRevision(plan);
            log.info("Finished admission confirmation: planId={}, state={}", planId, plan.getState());
        }

        // 6. Trả về kết quả xác nhận nhập học theo trạng thái hiện tại của kế hoạch.
        return toConfirmationResponse(planId, plan, planApplicants);
    }

    private <T> T executeInTransaction(Supplier<T> operation) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> operation.get()));
    }

    private record ConfirmationBatch(String confirmationToken, List<Long> applicantIds) {}

    private AdmissionConfirmationResponse toConfirmationResponse(
            Long planId, AdmissionPlan plan, List<AdmissionApplicant> planApplicants) {
        return AdmissionConfirmationResponse.builder()
                .planId(planId)
                .revision(plan.getRevision())
                .state(plan.getState().name())
                .enrolled((int) planApplicants.stream()
                        .filter(applicant -> applicant.getState() == AdmissionApplicantStatus.ENROLLED)
                        .count())
                .failed((int) planApplicants.stream()
                        .filter(applicant -> applicant.getState() == AdmissionApplicantStatus.FAILED)
                        .count())
                .skipped((int) planApplicants.stream()
                        .filter(applicant -> applicant.getState() == AdmissionApplicantStatus.EXISTING)
                        .count())
                .applicants(planQueryService.buildApplicantResponses(planApplicants))
                .build();
    }
}
