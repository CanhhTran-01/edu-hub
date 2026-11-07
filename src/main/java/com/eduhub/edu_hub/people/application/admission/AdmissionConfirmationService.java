package com.eduhub.edu_hub.people.application.admission;

import com.eduhub.edu_hub.identity.application.command.CreateStudentAccountCommand;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.AdmissionRequests.ConfirmRequest;
import com.eduhub.edu_hub.people.api.dto.response.AdmissionResponses.ConfirmationResponse;
import com.eduhub.edu_hub.people.application.code.StudentCodeGenerator;
import com.eduhub.edu_hub.people.domain.enums.AccountNotificationStatus;
import com.eduhub.edu_hub.people.domain.model.*;
import com.eduhub.edu_hub.people.domain.repository.*;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.exception.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class AdmissionConfirmationService {
    private static final String INITIAL_PASSWORD = "12345";
    private final AdmissionPlanRepository plans;
    private final AdmissionPlanClassRepository planClasses;
    private final AdmissionApplicantRepository applicants;
    private final AdministrativeClassRepository classes;
    private final StudentRepository students;
    private final StudentAdmissionRepository admissions;
    private final AdmissionNotificationRepository notifications;
    private final IdentityFacade identity;
    private final StudentCodeGenerator codes;
    private final AdmissionIdentityPolicy identityPolicy;
    private final AdmissionView view;
    private final AdmissionSettings settings;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;

    public ConfirmationResponse confirm(Long planId, ConfirmRequest request) {

        /*
        Chia thành nhiều transaction nhỏ,
        một cho bước chuẩn bị,
        mỗi thí sinh một transaction riêng,
        một cho bước kết thúc
        NẾU một thí sinh lỗi sẽ không làm hỏng cả đợt
        */

        // Chuẩn bị (transaction 1) -  kiểm tra, cấp token và hạn xử lý
        Work work = transaction(() -> prepare(planId, request));
        
        // Nhập học từng người (mỗi người một transaction) - xử lý từng hồ sơ, gia hạn sau mỗi hồ sơ
        for (Long applicantId : work.applicantIds()) {
            try {
                transaction(() -> {
                    enroll(planId, work.token(), applicantId);
                    return true;
                });

            } catch (RuntimeException exception) {

                // lỗi mức plan, dừng cả vòng lặp và ném ra ngoài
                if (exception instanceof BaseException base && base.getErrorCode() == ErrorCode.ADMISSION_INVALID_STATE)
                    throw exception;

                // Với các lỗi khác (lỗi riêng của người đó), mở transaction mới để ghi nhận thất bại
                transaction(() -> {
                    AdmissionPlan plan = requireToken(planId, work.token());
                    AdmissionApplicant row = applicants.findById(applicantId).orElseThrow();

                    if (row.isPending()) {
                        row.setState(AdmissionApplicant.State.FAILED);
                        row.setErrorMessage(exception instanceof BaseException base ? base.getErrorCode().name() : "Không thể lưu hồ sơ; có thể thử lại");
                    }

                    renew(plan);
                    return true;
                });

                log.warn("Admission row failed: planId={}, applicantId={}, failureType={}",
                        planId, applicantId, exception.getClass().getSimpleName());
            }
        }

        // Kết thúc (transaction cuối) - chốt trạng thái, xóa token và hạn xử lý, trả kết quả
        return transaction(() -> finish(planId, work.token(), request));
    }

    private Work prepare(Long id, ConfirmRequest request) {

        AdmissionPlan plan =
                plans.findForUpdate(id).orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_PLAN_NOT_FOUND));

        if (plan.getState() == AdmissionPlan.State.CONFIRMED) return new Work(null, List.of());
        if (plan.getRevision() != request.expectedRevision()) throw new BaseException(ErrorCode.ADMISSION_STALE_PLAN);

        boolean expired = plan.getState() == AdmissionPlan.State.CONFIRMING
                && plan.getProcessingUntil() != null
                && !plan.getProcessingUntil().isAfter(now());

        if (plan.getState() != AdmissionPlan.State.PREVIEWED
                && plan.getState() != AdmissionPlan.State.PARTIALLY_CONFIRMED
                && !expired) {
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
        }

        List<AdmissionApplicant> rows = applicants.findByPlan_IdOrderByRowNumber(id);
        List<AdmissionPlanClass> slots = planClasses.findByPlan_IdOrderByPosition(id);

        if (slots.isEmpty() || rows.stream().noneMatch(AdmissionApplicant::isIncluded)) {
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
        }

        validateProjected(plan, rows, slots, request);

        Map<Long, Integer> positions = new HashMap<>();
        slots.forEach(slot -> positions.put(slot.getAdministrativeClass().getId(), slot.getPosition()));

        List<Long> ids = rows.stream()
                .filter(AdmissionApplicant::isPending)
                .sorted(Comparator.comparingInt((AdmissionApplicant row) ->
                                positions.get(row.getAdministrativeClass().getId()))
                        .thenComparing(AdmissionApplicant::getFullName, AdmissionText.nameOrder())
                        .thenComparing(AdmissionApplicant::getApplicationCode))
                .map(AdmissionApplicant::getId)
                .toList();

        String token = UUID.randomUUID().toString();

        plan.setConfirmationToken(token);
        plan.setState(AdmissionPlan.State.CONFIRMING);
        plan.setExceptionReason(AdmissionText.optional(request.reason()));

        AdmissionWorkflowSupport.changed(plan);

        renew(plan);
        return new Work(token, ids);
    }

    private void validateProjected(
            AdmissionPlan plan, List<AdmissionApplicant> rows, List<AdmissionPlanClass> slots, ConfirmRequest request) {

        Set<Long> allowed = new HashSet<>();
        slots.forEach(slot -> allowed.add(slot.getAdministrativeClass().getId()));

        if (rows.stream()
                .filter(AdmissionApplicant::isIncluded)
                .anyMatch(row -> row.getAdministrativeClass() == null
                        || !allowed.contains(row.getAdministrativeClass().getId()))) {
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
        }

        for (AdmissionPlanClass slot : slots.stream()
                .sorted(Comparator.comparing(
                        value -> value.getAdministrativeClass().getId()))
                .toList()) {

            Long classId = slot.getAdministrativeClass().getId();
            AdministrativeClass target = classes.findByIdForStudentCodeUpdate(classId)
                    .orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE));

            if (target.isDeleted()
                    || target.getMaxCapacity() != plan.getMaxCapacity()
                    || target.getReserveCapacity() != plan.getReserveCapacity()
                    || !target.getMajor().getId().equals(plan.getMajor().getId())
                    || !target.getCohort().getId().equals(plan.getCohort().getId())) {
                throw new BaseException(ErrorCode.ADMISSION_DATA_CHANGED);
            }

            long projected = rows.stream()
                    .filter(AdmissionApplicant::isIncluded)
                    .filter(row -> row.getAdministrativeClass().getId().equals(classId))
                    .count();

            long ownEnrolled = rows.stream()
                    .filter(row -> row.getState() == AdmissionApplicant.State.ENROLLED)
                    .filter(row -> row.getAdministrativeClass().getId().equals(classId))
                    .count();

            if (students.countByAdministrativeClass_IdAndDeletedFalse(classId) != ownEnrolled) {
                throw new BaseException(ErrorCode.ADMISSION_DATA_CHANGED);
            }

            if (projected > plan.getMaxCapacity() + plan.getReserveCapacity()) {
                throw new BaseException(ErrorCode.CLASS_CAPACITY_EXCEEDED);
            }

            if ((projected > plan.getMaxCapacity() && !request.acceptReserve())
                    || (projected < plan.getMinCapacity()
                            && (!request.acceptBelowMinimum() || AdmissionText.optional(request.reason()) == null))) {
                throw new BaseException(ErrorCode.ADMISSION_WARNING_CONFIRMATION_REQUIRED);
            }
        }
    }

    private void enroll(Long planId, String token, Long applicantId) {

        // Lấy kế hoạch và hồ sơ thí sinh
        AdmissionPlan plan = requireToken(planId, token);
        AdmissionApplicant row = applicants.findById(applicantId).orElseThrow();

        // Hồ sơ phải thuộc đúng kế hoạch. Nếu hồ sơ không còn ở trạng thái chờ xử lý thì hàm dừng
        if (!row.getPlan().getId().equals(planId)) throw new BaseException(ErrorCode.VALIDATION_ERROR);
        if (!row.isPending()) return;

        // Nếu đã có lần nhập học mang cùng mã hồ sơ
        var existingAdmission = admissions.findByApplicationCode(row.getApplicationCode());
        if (existingAdmission.isPresent()) {

            // thông tin định danh không khớp
            if (!identityPolicy.same(row, existingAdmission.get())) {
                throw new BaseException(ErrorCode.ADMISSION_APPLICATION_CONFLICT);
            }

            row.setStudent(existingAdmission.get().getStudent());
            row.setReuseAccount(true);
            row.setState(AdmissionApplicant.State.EXISTING);
            row.setErrorMessage("Hồ sơ đã tồn tại; không cấp thêm MSSV hoặc tài khoản");

            renew(plan);
            return;
        }

        // Lấy lớp dự kiến nhập học
        AdministrativeClass target = classes.findByIdForStudentCodeUpdate(row.getAdministrativeClass().getId())
                .orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE));

        // Nếu số sinh viên chưa bị xóa trong lớp chạm ngưỡng "sĩ số tối đa + suất dự bị", nhả lỗi
        if (students.countByAdministrativeClass_IdAndDeletedFalse(target.getId())
                >= plan.getMaxCapacity() + plan.getReserveCapacity()) {
            throw new BaseException(ErrorCode.CLASS_CAPACITY_EXCEEDED);
        }

        // Tạo hoặc dùng lại sinh viên theo CCCD
        Student student = students.findByCitizenIdAndDeletedFalse(row.getCitizenId()).orElse(null);
        boolean reused = student != null;

        if (student == null) {
            // Chưa có sinh viên mang CCCD này
            // Sinh MSSV, kiểm tra trùng MSSV/tên đăng nhập, tạo tài khoản rồi tạo đối tượng Student

            String code = codes.generate(target, codes.nextSequence(target));

            if (students.existsByStudentCode(code) || identity.existsByUsername(code)) {
                throw new BaseException(ErrorCode.DUPLICATE_STUDENT_CODE);
            }

            Long userId = identity.createStudentAccountWithoutNotification(
                    new CreateStudentAccountCommand(code, row.getEmail(), INITIAL_PASSWORD, true));

            student = new Student();
            student.setStudentCode(code);
            student.setUserId(userId);
            student.setCitizenId(row.getCitizenId());
            student.setFullName(row.getFullName());
            student.setDateOfBirth(row.getDateOfBirth());
            student.setGender(row.getGender());
            student.setEmail(row.getEmail());
            student.setPhone(row.getPhone());

        } else {
            // Đã có sinh viên
            // 	Dùng lại sinh viên và tài khoản cũ; nếu tài khoản bị khóa thì ghi cảnh báo, giữ nguyên trạng thái khóa
            var account = identity.getStudentAccount(student.getUserId());
            if (account.locked())
                row.setWarningMessage("Tài khoản cũ đang khóa/ngừng hoạt động; giữ nguyên trạng thái");
        }

        // Cập nhật thông tin học tập và lưu sinh viên
        student.setAdministrativeClass(target);
        student.setEntranceScore(row.getEntranceScore());
        student.setActivityStatus(ActivityStatus.STUDYING);
        student.setAccountNotificationStatus(
                row.getEmail() == null ? AccountNotificationStatus.SKIPPED : AccountNotificationStatus.PENDING);
        students.saveAndFlush(student);

        // Tạo bản ghi nhập học
        StudentAdmission admission = new StudentAdmission();
        admission.setApplicationCode(row.getApplicationCode());
        admission.setStudent(student);
        admission.setPlan(plan);
        admission.setAdministrativeClass(target);
        admission.setCitizenId(row.getCitizenId());
        admission.setFullName(row.getFullName());
        admission.setDateOfBirth(row.getDateOfBirth());
        admission.setGender(row.getGender());
        admission.setEmail(row.getEmail());
        admission.setPhone(row.getPhone());
        admission.setEntranceScore(row.getEntranceScore());
        admissions.saveAndFlush(admission);

        /*
           Tạo thông báo chờ gửi
            -Dùng lại sinh viên → thông báo nhập học lại.
            -Tạo sinh viên mới → thông báo tài khoản ban đầu.
            -Không có email → SKIPPED, ghi lý do.
            -Có email → PENDING.
        */
        AdmissionNotification notification = new AdmissionNotification();
        notification.setAdmission(admission);
        notification.setStudent(student);
        notification.setRecipient(row.getEmail());
        notification.setNextAttemptAt(now());

        notification.setKind(
                reused ? AdmissionNotification.Kind.RE_ENROLLMENT : AdmissionNotification.Kind.INITIAL_ACCOUNT);

        notification.setState(
                row.getEmail() == null ? AdmissionNotification.State.SKIPPED : AdmissionNotification.State.PENDING);

        if (row.getEmail() == null) notification.setLastError("NO_EMAIL");

        notifications.saveAndFlush(notification);

        row.setStudent(student);
        row.setReuseAccount(reused);
        row.setState(AdmissionApplicant.State.ENROLLED);
        row.setErrorMessage(null);

        renew(plan);

        events.publishEvent(new AdmissionQueued(notification.getId()));

        // TODO(AUDIT_LOG): Record per-student enrollment and whether the existing identity was reused.
    }

    private ConfirmationResponse finish(Long id, String token, ConfirmRequest request) {
        AdmissionPlan plan = plans.findForUpdate(id).orElseThrow();
        if (token != null) {
            requireToken(plan, token);
            List<AdmissionApplicant> rows = applicants.findByPlan_IdOrderByRowNumber(id);
            boolean pending = rows.stream().anyMatch(AdmissionApplicant::isPending);
            boolean unacceptedUnderMinimum = !request.acceptBelowMinimum()
                    && planClasses.findByPlan_IdOrderByPosition(id).stream()
                            .anyMatch(slot -> rows.stream()
                                            .filter(AdmissionApplicant::isIncluded)
                                            .filter(row -> row.getAdministrativeClass() != null
                                                    && row.getAdministrativeClass()
                                                            .getId()
                                                            .equals(slot.getAdministrativeClass()
                                                                    .getId()))
                                            .count()
                                    < plan.getMinCapacity());
            plan.setState(
                    pending || unacceptedUnderMinimum
                            ? AdmissionPlan.State.PARTIALLY_CONFIRMED
                            : AdmissionPlan.State.CONFIRMED);
            plan.setTotalStudents(
                    (int) rows.stream().filter(AdmissionApplicant::isIncluded).count());
            plan.setConfirmationToken(null);
            plan.setProcessingUntil(null);
            AdmissionWorkflowSupport.changed(plan);
            log.info("Finished admission confirmation: planId={}, state={}", id, plan.getState());
        }
        List<AdmissionApplicant> rows = applicants.findByPlan_IdOrderByRowNumber(id);
        return new ConfirmationResponse(
                id,
                plan.getRevision(),
                plan.getState().name(),
                (int) rows.stream()
                        .filter(row -> row.getState() == AdmissionApplicant.State.ENROLLED)
                        .count(),
                (int) rows.stream()
                        .filter(row -> row.getState() == AdmissionApplicant.State.FAILED)
                        .count(),
                (int) rows.stream()
                        .filter(row -> row.getState() == AdmissionApplicant.State.EXISTING)
                        .count(),
                rows.stream().map(view::applicant).toList());
    }

    private AdmissionPlan requireToken(Long id, String token) {
        AdmissionPlan plan = plans.findForUpdate(id).orElseThrow();
        requireToken(plan, token);
        return plan;
    }

    private void requireToken(AdmissionPlan plan, String token) {
        // Token không được null.
        // Kế hoạch phải đang ở trạng thái CONFIRMING.
        // Token truyền vào phải trùng confirmationToken của kế hoạch.
        if (token == null
                || plan.getState() != AdmissionPlan.State.CONFIRMING
                || !token.equals(plan.getConfirmationToken()))
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
    }

    private void renew(AdmissionPlan plan) {
        // gia hạn thời gian xử lý của kế hoạch tuyển sinh
        plan.setProcessingUntil(now().plusMinutes(Math.max(1, settings.getConfirmationLeaseMinutes())));
    }

    private <T> T transaction(Supplier<T> supplier) {
        return Objects.requireNonNull(transactions.execute(status -> supplier.get()));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private record Work(String token, List<Long> applicantIds) {}
}
