package com.eduhub.edu_hub.enrollment.application.service;

import com.eduhub.edu_hub.enrollment.api.dto.request.ImportProcessScoresRequest;
import com.eduhub.edu_hub.enrollment.api.dto.request.UpdateProcessScoresRequest;
import com.eduhub.edu_hub.enrollment.api.dto.response.ModuleClassStudentResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.ProcessScoreRosterResponse;
import com.eduhub.edu_hub.enrollment.api.mapper.ModuleClassStudentResponseMapper;
import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.enrollment.domain.model.Enrollment;
import com.eduhub.edu_hub.enrollment.domain.policy.ProcessScorePolicy;
import com.eduhub.edu_hub.enrollment.domain.repository.EnrollmentRepository;
import com.eduhub.edu_hub.enrollment.infrastructure.spreadsheet.ProcessScoreSpreadsheet;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.domain.enums.AccessTokenPurpose;
import com.eduhub.edu_hub.identity.domain.enums.RoleName;
import com.eduhub.edu_hub.people.api.dto.response.StudentRosterProfileResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassGradingResponse;
import com.eduhub.edu_hub.scheduling.application.facade.SchedulingFacade;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Điều phối nhập điểm trong một transaction; đọc Excel ngoài thời gian giữ khóa dữ liệu. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessScoreService {
    private static final int MAX_STUDENTS_PER_REQUEST = 1000;
    private static final Set<EnrollmentStatus> GRADABLE_STATUSES = Set.of(EnrollmentStatus.REGISTERED);
    private final EnrollmentRepository enrollmentRepository;
    private final IdentityFacade identityFacade;
    private final PeopleFacade.Scheduling lecturerFacade;
    private final PeopleFacade.Enrollment studentFacade;
    private final SchedulingFacade.Grading schedulingFacade;
    private final ProcessScoreSpreadsheet spreadsheet;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    private record GradingActor(Long userId, Long lecturerId) {}

    private record GradingRoster(List<Enrollment> enrollments, List<StudentRosterProfileResponse> studentProfiles) {
        List<ModuleClassStudentResponse> toStudentResponses() {
            return ModuleClassStudentResponseMapper.toResponses(enrollments, studentProfiles);
        }
    }

    private record ProcessScoreChange(
            Enrollment enrollment, BigDecimal firstScore, BigDecimal secondScore, String note) {}

    public ProcessScoreRosterResponse findScores(Jwt principal, Long moduleClassId) {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> {
            var actor = requireLecturer(principal);
            var moduleClass = requireAssignedClass(actor, moduleClassId, false);
            return new ProcessScoreRosterResponse(
                    moduleClass, loadRoster(moduleClassId).toStudentResponses());
        });
    }

    public byte[] createTemplate(Jwt principal, Long moduleClassId) {
        // Không giữ transaction đọc khi POI dựng tệp; danh tính và phân công đã được kiểm tra trong snapshot.
        return spreadsheet.writeTemplate(findScores(principal, moduleClassId));
    }

    public ProcessScoreRosterResponse updateScores(
            Jwt principal, Long moduleClassId, UpdateProcessScoresRequest request) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            var actor = requireLecturer(principal);
            var moduleClass = requireAssignedClass(actor, moduleClassId, true);
            if (request == null
                    || request.getStudents() == null
                    || request.getStudents().isEmpty()
                    || request.getStudents().size() > MAX_STUDENTS_PER_REQUEST)
                throw invalidScores("students", "Phải chọn từ 1 đến 1000 lượt học để cập nhật");

            var roster = loadRoster(moduleClassId);
            roster.toStudentResponses();

            var enrollmentsById =
                    roster.enrollments().stream().collect(Collectors.toMap(Enrollment::getId, Function.identity()));
            List<FieldErrorDetail> errors = new ArrayList<>();
            List<ProcessScoreChange> changes = new ArrayList<>();
            Set<Long> requestedEnrollmentIds = new HashSet<>();

            // Kiểm tra toàn bộ yêu cầu trước khi chạm vào entity đang quản lý, kể cả khi một dòng đã sai.
            for (int studentIndex = 0; studentIndex < request.getStudents().size(); studentIndex++) {
                var studentRequest = request.getStudents().get(studentIndex);
                String prefix = "students[" + studentIndex + "]";
                if (studentRequest == null) {
                    errors.add(new FieldErrorDetail(prefix, "Dòng cập nhật không được null"));
                    continue;
                }
                Long enrollmentId = studentRequest.getEnrollmentId();
                var enrollment = enrollmentsById.get(enrollmentId);
                if (enrollmentId == null || enrollmentId <= 0 || enrollment == null)
                    errors.add(new FieldErrorDetail(
                            prefix + ".enrollmentId", "Lượt học không thuộc danh sách REGISTERED của lớp"));
                if (enrollmentId != null && !requestedEnrollmentIds.add(enrollmentId))
                    errors.add(
                            new FieldErrorDetail(prefix + ".enrollmentId", "Một lượt học chỉ được xuất hiện một lần"));
                validateValues(
                        studentRequest.getComponentScore1(),
                        studentRequest.getComponentScore2(),
                        studentRequest.getNote(),
                        prefix,
                        errors);
                if (enrollment != null)
                    changes.add(new ProcessScoreChange(
                            enrollment,
                            studentRequest.hasComponentScore1()
                                    ? studentRequest.getComponentScore1()
                                    : enrollment.getComponentScore1(),
                            studentRequest.hasComponentScore2()
                                    ? studentRequest.getComponentScore2()
                                    : enrollment.getComponentScore2(),
                            studentRequest.hasNote()
                                    ? ProcessScorePolicy.normalizeNote(studentRequest.getNote())
                                    : enrollment.getNote()));
            }
            if (!errors.isEmpty()) throw new EnrollmentException(ErrorCode.INVALID_SCORE, errors);
            applyChanges(changes);
            return new ProcessScoreRosterResponse(moduleClass, roster.toStudentResponses());
        });
    }

    public ProcessScoreRosterResponse importScores(
            Jwt principal, Long moduleClassId, ImportProcessScoresRequest request) {
        // Chặn người không phụ trách trước khi đọc tệp; sau parse vẫn xác thực và khóa/check lại trong transaction ghi.
        var initialRoster = findScores(principal, moduleClassId);
        var imported = spreadsheet.read(request == null ? null : request.file(), initialRoster.moduleClass());
        return new TransactionTemplate(transactionManager).execute(status -> {
            var actor = requireLecturer(principal);
            var moduleClass = requireAssignedClass(actor, moduleClassId, true);
            var roster = loadRoster(moduleClassId);
            var studentsByCode = roster.toStudentResponses().stream()
                    .collect(Collectors.toMap(ModuleClassStudentResponse::studentCode, Function.identity()));
            var enrollmentsById =
                    roster.enrollments().stream().collect(Collectors.toMap(Enrollment::getId, Function.identity()));
            List<FieldErrorDetail> errors = new ArrayList<>(imported.errors());
            List<ProcessScoreChange> changes = new ArrayList<>();
            Set<String> requestedStudentCodes = new HashSet<>();
            for (var importedStudent : imported.students()) {
                String prefix = "rows[" + importedStudent.rowNumber() + "]";
                var student = studentsByCode.get(importedStudent.studentCode());
                if (importedStudent.studentCode() != null && student == null)
                    errors.add(new FieldErrorDetail(
                            prefix + ".studentCode", "MSSV không thuộc danh sách REGISTERED của lớp"));
                if (importedStudent.studentCode() != null && !requestedStudentCodes.add(importedStudent.studentCode()))
                    errors.add(new FieldErrorDetail(prefix + ".studentCode", "MSSV bị lặp trong tệp"));
                validateValues(
                        importedStudent.componentScore1(),
                        importedStudent.componentScore2(),
                        importedStudent.note(),
                        prefix,
                        errors);
                if (student != null) {
                    var enrollment = enrollmentsById.get(student.enrollmentId());
                    changes.add(new ProcessScoreChange(
                            enrollment,
                            importedStudent.componentScore1(),
                            importedStudent.componentScore2(),
                            importedStudent.note() == null
                                            || importedStudent.note().isBlank()
                                    ? enrollment.getNote()
                                    : ProcessScorePolicy.normalizeNote(importedStudent.note())));
                }
            }
            if (!errors.isEmpty()) throw new EnrollmentException(ErrorCode.PROCESS_SCORE_IMPORT_INVALID, errors);
            applyChanges(changes);
            return new ProcessScoreRosterResponse(moduleClass, roster.toStudentResponses());
        });
    }

    private GradingActor requireLecturer(Jwt principal) {
        if (principal == null) throw new EnrollmentException(ErrorCode.UNAUTHENTICATED);
        if (!AccessTokenPurpose.ACCESS.name().equals(principal.getClaim(AccessTokenPurpose.CLAIM_NAME)))
            throw new EnrollmentException(ErrorCode.FIRST_LOGIN_REQUIRED);
        var currentUser = identityFacade.requireCurrentUser(principal);
        if (!RoleName.LECTURER.name().equals(currentUser.getRole()))
            throw new EnrollmentException(ErrorCode.UNAUTHORIZED);
        Long lecturerId = lecturerFacade
                .findLecturerIdByUserId(currentUser.getId())
                .orElseThrow(() -> new EnrollmentException(ErrorCode.UNAUTHORIZED));
        return new GradingActor(currentUser.getId(), lecturerId);
    }

    private ModuleClassGradingResponse requireAssignedClass(
            GradingActor actor, Long moduleClassId, boolean shouldLock) {
        if (moduleClassId == null || moduleClassId <= 0) throw new EnrollmentException(ErrorCode.VALIDATION_ERROR);
        var moduleClass = shouldLock
                ? schedulingFacade.lockModuleClassForGrading(moduleClassId)
                : schedulingFacade.findModuleClassForGrading(moduleClassId);
        if (!actor.lecturerId().equals(moduleClass.lecturerId())) {
            log.warn(
                    "Từ chối truy cập điểm quá trình: actorUserId={}, moduleClassId={}", actor.userId(), moduleClassId);
            throw new EnrollmentException(ErrorCode.UNAUTHORIZED);
        }
        if (moduleClass.status() != ModuleClassStatus.CLOSED && moduleClass.status() != ModuleClassStatus.COMPLETED)
            throw new EnrollmentException(ErrorCode.PROCESS_SCORE_CLASS_NOT_READY);

        // Hạn khóa thuộc từng Enrollment; kiểm tra lại ngay trước khi ghi, không khóa toàn học kỳ.
        return moduleClass;
    }

    private GradingRoster loadRoster(Long moduleClassId) {
        var enrollments = enrollmentRepository.findAllByModuleClassIdAndDeletedFalseAndStatusInOrderByIdAsc(
                moduleClassId, GRADABLE_STATUSES);
        var profiles = enrollments.isEmpty()
                ? List.<StudentRosterProfileResponse>of()
                : studentFacade.findRosterStudentsByIds(
                        enrollments.stream().map(Enrollment::getStudentId).collect(Collectors.toSet()));
        return new GradingRoster(enrollments, profiles);
    }

    private void validateValues(
            BigDecimal firstScore, BigDecimal secondScore, String note, String prefix, List<FieldErrorDetail> errors) {
        if (!ProcessScorePolicy.isValidComponentScore(firstScore))
            errors.add(new FieldErrorDetail(
                    prefix + ".componentScore1", "TP1 phải trong khoảng 0–10, tối đa 3 chữ số thập phân"));
        if (!ProcessScorePolicy.isValidComponentScore(secondScore))
            errors.add(new FieldErrorDetail(
                    prefix + ".componentScore2", "TP2 phải trong khoảng 0–10, tối đa 3 chữ số thập phân"));
        if (note != null && note.length() > ProcessScorePolicy.MAX_NOTE_LENGTH)
            errors.add(new FieldErrorDetail(prefix + ".note", "Ghi chú tối đa 500 ký tự"));
    }

    private void applyChanges(List<ProcessScoreChange> changes) {
        var currentBusinessTime = LocalDateTime.now(clock);
        // Kiểm tra cả lô trước khi sửa; trạng thái khóa không phụ thuộc scheduler hoặc thời điểm tải giao diện.
        for (var change : changes) {
            if (change.enrollment().isGradeLocked(currentBusinessTime))
                throw new EnrollmentException(ErrorCode.GRADE_LOCKED);
        }
        var updatedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (var change : changes) {
            var enrollment = change.enrollment();
            var processScore = ProcessScorePolicy.calculateProcessScore(change.firstScore(), change.secondScore());
            if (ProcessScorePolicy.hasSameScore(enrollment.getComponentScore1(), change.firstScore())
                    && ProcessScorePolicy.hasSameScore(enrollment.getComponentScore2(), change.secondScore())
                    && ProcessScorePolicy.hasSameScore(enrollment.getProcessScore(), processScore)
                    && Objects.equals(enrollment.getNote(), change.note())) continue;
            enrollment.updateProcessScores(
                    change.firstScore(), change.secondScore(), processScore, change.note(), updatedAt);
        }
        // TODO(AUDIT_LOG): Bổ sung lịch sử actor/lượt học/lớp, nguồn nhập và giá trị trước/sau khi triển khai audit.
        //  Hiện không ghi audit và không đưa điểm hoặc nội dung ghi chú vào application log.
        enrollmentRepository.flush();
    }

    private EnrollmentException invalidScores(String field, String message) {
        return new EnrollmentException(ErrorCode.INVALID_SCORE, List.of(new FieldErrorDetail(field, message)));
    }
}
