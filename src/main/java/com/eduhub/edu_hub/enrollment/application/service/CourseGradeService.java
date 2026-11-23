package com.eduhub.edu_hub.enrollment.application.service;

import com.eduhub.edu_hub.enrollment.api.dto.request.*;
import com.eduhub.edu_hub.enrollment.api.dto.response.CourseGradeResponse;
import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.enrollment.application.specification.CourseGradeSpecification;
import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.enrollment.domain.model.Enrollment;
import com.eduhub.edu_hub.enrollment.domain.policy.CourseGradePolicy;
import com.eduhub.edu_hub.enrollment.domain.policy.ProcessScorePolicy;
import com.eduhub.edu_hub.enrollment.domain.repository.EnrollmentRepository;
import com.eduhub.edu_hub.enrollment.infrastructure.config.CourseGradeSettings;
import com.eduhub.edu_hub.enrollment.infrastructure.spreadsheet.CourseGradeSpreadsheet;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.domain.enums.AccessTokenPurpose;
import com.eduhub.edu_hub.identity.domain.enums.RoleName;
import com.eduhub.edu_hub.people.api.dto.response.StudentRosterProfileResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassGradingResponse;
import com.eduhub.edu_hub.scheduling.application.facade.SchedulingFacade;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.shared.common.*;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.mapper.PageMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Admin học vụ nhập điểm thi; khóa kỳ/lớp và ghi cả lô trong một transaction, không giữ khóa khi đọc Excel. */
@Service
@lombok.RequiredArgsConstructor
public class CourseGradeService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<EnrollmentStatus> VISIBLE_STATUSES =
            Set.of(EnrollmentStatus.REGISTERED, EnrollmentStatus.COMPLETED, EnrollmentStatus.FAILED);
    private final EnrollmentRepository enrollmentRepository;
    private final IdentityFacade identityFacade;
    private final PeopleFacade.Enrollment studentFacade;
    private final SchedulingFacade.Grading schedulingFacade;
    private final CourseGradeSpreadsheet spreadsheet;
    private final CourseGradeSettings settings;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    private record GradeRoster(List<Enrollment> enrollments, Map<Long, StudentRosterProfileResponse> profilesById) {}

    private record TemplateSnapshot(ModuleClassGradingResponse moduleClass, List<CourseGradeResponse> students) {}

    private record GradeChange(Enrollment enrollment, BigDecimal finalExamScore, CourseGradePolicy.GradeResult grade) {}

    public PageResponse<CourseGradeResponse> findCourseGrades(Jwt principal, SearchCourseGradesRequest request) {
        return readTransaction().execute(transaction -> {
            requireAcademicAdmin(principal);
            validateSearchRequest(request);
            Set<Long> studentIds =
                    request.getKeyword() == null || request.getKeyword().isBlank()
                            ? null
                            : studentFacade.findRosterStudentIdsByKeyword(request.getKeyword());
            var enrollments = enrollmentRepository.findAll(
                    CourseGradeSpecification.fromRequest(request, studentIds),
                    PageRequest.of(request.getPage(), request.getSize(), Sort.by(Sort.Direction.DESC, "id")));
            var profilesById = loadProfiles(enrollments.getContent());
            var currentBusinessTime = LocalDateTime.now(clock);
            return PageMapper.toPageResponse(enrollments.map(enrollment ->
                    toResponse(enrollment, profilesById.get(enrollment.getStudentId()), currentBusinessTime)));
        });
    }

    public CourseGradeResponse updateCourseGrade(Jwt principal, Long enrollmentId, UpdateCourseGradeRequest request) {
        return new TransactionTemplate(transactionManager).execute(transaction -> {
            requireAcademicAdmin(principal);
            requirePositiveId(enrollmentId, "enrollmentId");
            // Chỉ đọc trước khi chờ khóa để không giữ điểm cũ trong persistence context.
            Long moduleClassId = enrollmentRepository
                    .findModuleClassIdByEnrollmentId(enrollmentId)
                    .orElseThrow(() -> new EnrollmentException(ErrorCode.ENROLLMENT_NOT_FOUND));
            requireGradableClass(moduleClassId, true);
            var enrollment = enrollmentRepository
                    .findByIdAndDeletedFalse(enrollmentId)
                    .orElseThrow(() -> new EnrollmentException(ErrorCode.ENROLLMENT_NOT_FOUND));
            if (!enrollment.getModuleClassId().equals(moduleClassId)
                    || !VISIBLE_STATUSES.contains(enrollment.getStatus()))
                throw new EnrollmentException(ErrorCode.ENROLLMENT_NOT_FOUND);
            var currentBusinessTime = LocalDateTime.now(clock);
            List<FieldErrorDetail> errors = new ArrayList<>();
            BigDecimal examScore = request == null ? null : request.getFinalExamScore();
            validateChange(enrollment, examScore, "", currentBusinessTime, errors);
            if (enrollment.isGradeLocked(currentBusinessTime))
                throw new EnrollmentException(ErrorCode.GRADE_LOCKED, errors);
            if (!errors.isEmpty()) throw new EnrollmentException(ErrorCode.INVALID_SCORE, errors);
            var profilesById = loadProfiles(List.of(enrollment));
            var grade = CourseGradePolicy.calculateGrade(
                    enrollment.getProcessScore(), Objects.requireNonNull(examScore), requireConfiguredGradeScale());
            applyChanges(List.of(new GradeChange(enrollment, examScore, grade)));
            return toResponse(enrollment, profilesById.get(enrollment.getStudentId()), LocalDateTime.now(clock));
        });
    }

    public byte[] createTemplate(Jwt principal, Long moduleClassId) {
        TemplateSnapshot snapshot = readTransaction().execute(transaction -> {
            requireAcademicAdmin(principal);
            var moduleClass = requireGradableClass(moduleClassId, false);
            var roster = loadRoster(moduleClassId);
            var currentBusinessTime = LocalDateTime.now(clock);
            // Mẫu chỉ chứa lượt còn được nhập; dòng khóa vẫn xem được qua API tìm kiếm và bảng điểm sinh viên.
            var students = roster.enrollments().stream()
                    .filter(enrollment -> !enrollment.isGradeLocked(currentBusinessTime))
                    .map(enrollment -> toResponse(
                            enrollment, roster.profilesById().get(enrollment.getStudentId()), currentBusinessTime))
                    .sorted(Comparator.comparing(CourseGradeResponse::studentCode)
                            .thenComparing(CourseGradeResponse::enrollmentId))
                    .toList();
            return new TemplateSnapshot(moduleClass, students);
        });
        return spreadsheet.writeTemplate(snapshot.moduleClass(), snapshot.students());
    }

    public List<CourseGradeResponse> importCourseGrades(
            Jwt principal, Long moduleClassId, ImportCourseGradesRequest request) {
        var initialClass = readTransaction().execute(transaction -> {
            requireAcademicAdmin(principal);
            return requireGradableClass(moduleClassId, false);
        });
        var imported = spreadsheet.read(request == null ? null : request.file(), initialClass);
        return new TransactionTemplate(transactionManager).execute(transaction -> {
            requireAcademicAdmin(principal);
            var moduleClass = requireGradableClass(moduleClassId, true);
            if (!moduleClass.moduleClassCode().equals(initialClass.moduleClassCode())
                    || !moduleClass.semesterCode().equals(initialClass.semesterCode()))
                throw new EnrollmentException(
                        ErrorCode.COURSE_GRADE_IMPORT_INVALID,
                        List.of(new FieldErrorDetail("file", "Lớp hoặc kỳ đã thay đổi khi đọc tệp; hãy tải lại mẫu")));
            var roster = loadRoster(moduleClassId);
            Map<String, List<Enrollment>> enrollmentsByStudentCode = roster.enrollments().stream()
                    .collect(Collectors.groupingBy(enrollment ->
                            roster.profilesById().get(enrollment.getStudentId()).studentCode()));
            var currentBusinessTime = LocalDateTime.now(clock);
            List<FieldErrorDetail> errors = new ArrayList<>(imported.errors());
            Set<String> requestedStudentCodes = new HashSet<>();
            Map<Enrollment, BigDecimal> requestedScores = new LinkedHashMap<>();
            // Kiểm tra mọi dòng trước khi sửa entity; thứ tự file không quyết định lượt học được cập nhật.
            for (var importedStudent : imported.students()) {
                String prefix = "rows[" + importedStudent.rowNumber() + "].";
                String studentCode = importedStudent.studentCode();
                if (studentCode == null || studentCode.isBlank()) continue;
                if (!requestedStudentCodes.add(studentCode))
                    errors.add(new FieldErrorDetail(prefix + "studentCode", "MSSV bị lặp trong tệp"));
                var matches = enrollmentsByStudentCode.getOrDefault(studentCode, List.of());
                if (matches.size() != 1) {
                    errors.add(new FieldErrorDetail(
                            prefix + "studentCode",
                            matches.isEmpty()
                                    ? "MSSV không có lượt học còn hiệu lực trong lớp đang chọn"
                                    : "MSSV khớp nhiều lượt học; cần đối soát dữ liệu, không tự chọn lần học"));
                    continue;
                }
                var enrollment = matches.getFirst();
                validateChange(enrollment, importedStudent.finalExamScore(), prefix, currentBusinessTime, errors);
                requestedScores.put(enrollment, importedStudent.finalExamScore());
            }
            if (!errors.isEmpty()) throw new EnrollmentException(ErrorCode.COURSE_GRADE_IMPORT_INVALID, errors);
            var gradeScale = requireConfiguredGradeScale();
            var changes = requestedScores.entrySet().stream()
                    .map(entry -> new GradeChange(
                            entry.getKey(),
                            entry.getValue(),
                            CourseGradePolicy.calculateGrade(
                                    entry.getKey().getProcessScore(), entry.getValue(), gradeScale)))
                    .toList();
            applyChanges(changes);
            var responseBusinessTime = LocalDateTime.now(clock);
            return changes.stream()
                    .map(change -> toResponse(
                            change.enrollment(),
                            roster.profilesById().get(change.enrollment().getStudentId()),
                            responseBusinessTime))
                    .sorted(Comparator.comparing(CourseGradeResponse::studentCode)
                            .thenComparing(CourseGradeResponse::enrollmentId))
                    .toList();
        });
    }

    private void requireAcademicAdmin(Jwt principal) {
        if (principal == null) throw new EnrollmentException(ErrorCode.UNAUTHENTICATED);
        if (!AccessTokenPurpose.ACCESS.name().equals(principal.getClaim(AccessTokenPurpose.CLAIM_NAME)))
            throw new EnrollmentException(ErrorCode.FIRST_LOGIN_REQUIRED);
        var currentUser = identityFacade.requireCurrentUser(principal);
        if (!RoleName.ACADEMIC_ADMIN.name().equals(currentUser.getRole()))
            throw new EnrollmentException(ErrorCode.UNAUTHORIZED);
    }

    private ModuleClassGradingResponse requireGradableClass(Long moduleClassId, boolean shouldLock) {
        requirePositiveId(moduleClassId, "moduleClassId");
        var moduleClass = shouldLock
                ? schedulingFacade.lockModuleClassForGrading(moduleClassId)
                : schedulingFacade.findModuleClassForGrading(moduleClassId);
        if (moduleClass.status() != ModuleClassStatus.CLOSED && moduleClass.status() != ModuleClassStatus.COMPLETED)
            throw new EnrollmentException(ErrorCode.PROCESS_SCORE_CLASS_NOT_READY);
        return moduleClass;
    }

    private GradeRoster loadRoster(Long moduleClassId) {
        var enrollments = enrollmentRepository.findAllByModuleClassIdAndDeletedFalseAndStatusInOrderByIdAsc(
                moduleClassId, VISIBLE_STATUSES);
        return new GradeRoster(enrollments, loadProfiles(enrollments));
    }

    private Map<Long, StudentRosterProfileResponse> loadProfiles(List<Enrollment> enrollments) {
        if (enrollments.isEmpty()) return Map.of();
        var studentIds = enrollments.stream().map(Enrollment::getStudentId).collect(Collectors.toSet());
        var profilesById = studentFacade.findRosterStudentsByIds(studentIds).stream()
                .collect(Collectors.toMap(StudentRosterProfileResponse::studentId, Function.identity()));
        if (!profilesById.keySet().containsAll(studentIds)
                || profilesById.values().stream()
                        .anyMatch(profile -> profile.studentCode() == null
                                || profile.studentCode().isBlank()))
            throw new EnrollmentException(ErrorCode.ENROLLMENT_DATA_INCOMPLETE);
        return profilesById;
    }

    private void validateChange(
            Enrollment enrollment,
            BigDecimal examScore,
            String prefix,
            LocalDateTime currentBusinessTime,
            List<FieldErrorDetail> errors) {
        if (examScore == null || !ProcessScorePolicy.isValidComponentScore(examScore))
            errors.add(new FieldErrorDetail(
                    prefix + "finalExamScore", "Điểm thi bắt buộc từ 0–10, tối đa 3 chữ số thập phân có nghĩa"));
        if (enrollment.isGradeLocked(currentBusinessTime))
            errors.add(new FieldErrorDetail(prefix + "finalExamScore", "Lượt học đã khóa điểm, không được cập nhật"));
        if (enrollment.getProcessScore() == null)
            errors.add(new FieldErrorDetail(
                    prefix + "processScore", "Cần nhập đủ TP1 và TP2 để có điểm quá trình trước khi nhập điểm thi"));
        else if (!ProcessScorePolicy.isValidComponentScore(enrollment.getProcessScore()))
            throw new EnrollmentException(ErrorCode.ENROLLMENT_DATA_INCOMPLETE);
    }

    private void applyChanges(List<GradeChange> changes) {
        var currentBusinessTime = LocalDateTime.now(clock);
        // Có thể vừa hết hạn trong lúc tải hồ sơ/kiểm tra tệp; kiểm tra lại cả lô trước lần sửa entity đầu tiên.
        for (var change : changes) {
            if (change.enrollment().isGradeLocked(currentBusinessTime))
                throw new EnrollmentException(ErrorCode.GRADE_LOCKED);
        }
        // Chốt cấu hình một lần cho cả lô công bố mới; kết quả đã công bố giữ nguyên hạn dù cấu hình đổi.
        LocalDateTime initialGradeLockAt =
                changes.stream().anyMatch(change -> change.enrollment().getGradePublishedAt() == null)
                        ? calculateInitialGradeLockAt(currentBusinessTime)
                        : null;
        var auditTimestamp = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (var change : changes) {
            var enrollment = change.enrollment();
            var grade = change.grade();
            var status = grade.passed() ? EnrollmentStatus.COMPLETED : EnrollmentStatus.FAILED;
            if (ProcessScorePolicy.hasSameScore(enrollment.getFinalExamScore(), change.finalExamScore())
                    && ProcessScorePolicy.hasSameScore(enrollment.getCourseScore10(), grade.score10())
                    && ProcessScorePolicy.hasSameScore(enrollment.getCourseScore4(), grade.score4())
                    && Objects.equals(enrollment.getCourseScoreLetter(), grade.letter())
                    && enrollment.getStatus() == status
                    && enrollment.getGradePublishedAt() != null) continue;
            enrollment.updateCourseGrade(
                    change.finalExamScore(),
                    grade.score10(),
                    grade.letter(),
                    grade.score4(),
                    status,
                    currentBusinessTime,
                    initialGradeLockAt,
                    auditTimestamp);
        }
        // TODO(AUDIT_LOG): Ghi actor, nguồn nhập và điểm trước/sau khi có hạ tầng audit; không log nội dung bảng điểm.
        enrollmentRepository.flush();
    }

    private CourseGradeResponse toResponse(
            Enrollment enrollment, StudentRosterProfileResponse profile, LocalDateTime currentBusinessTime) {
        return new CourseGradeResponse(
                enrollment.getId(),
                enrollment.getStudentId(),
                profile.studentCode(),
                profile.fullName(),
                enrollment.getModuleClassId(),
                enrollment.getModuleClassCode(),
                enrollment.getSemesterId(),
                enrollment.getSubjectId(),
                enrollment.getSubjectCode(),
                enrollment.getSubjectName(),
                enrollment.getAcademicYearStart(),
                enrollment.getSemesterNumber(),
                enrollment.getCredits(),
                enrollment.getRegisteredAt(),
                enrollment.getStatus(),
                enrollment.getComponentScore1(),
                enrollment.getComponentScore2(),
                enrollment.getProcessScore(),
                enrollment.getFinalExamScore(),
                enrollment.getCourseScore10(),
                enrollment.getCourseScoreLetter(),
                enrollment.getCourseScore4(),
                enrollment.getGradePublishedAt(),
                enrollment.getGradeLockAt(),
                enrollment.isGradeLocked(currentBusinessTime));
    }

    private TransactionTemplate readTransaction() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction;
    }

    private LocalDateTime calculateInitialGradeLockAt(LocalDateTime gradePublishedAt) {
        try {
            return CourseGradePolicy.calculateGradeLockAt(gradePublishedAt, settings.getGradeCorrectionWindow());
        } catch (EnrollmentException exception) {
            throw new EnrollmentException(
                    exception.getErrorCode(),
                    List.of(new FieldErrorDetail(
                            "enrollment.grading.grade-correction-window",
                            "Thời hạn phải không âm và nằm trong phạm vi timestamp có thể lưu")));
        }
    }

    private List<CourseGradePolicy.GradeBand> requireConfiguredGradeScale() {
        try {
            return CourseGradePolicy.requireValidScale(settings.getGradeScale());
        } catch (EnrollmentException exception) {
            throw new EnrollmentException(
                    exception.getErrorCode(),
                    List.of(new FieldErrorDetail(
                            "enrollment.grading.grade-scale",
                            "Cần cấu hình thang quy đổi điểm chính thức trước khi nhập điểm thi")));
        }
    }

    private void validateSearchRequest(SearchCourseGradesRequest request) {
        if (request == null) throw new EnrollmentException(ErrorCode.VALIDATION_ERROR);
        List<FieldErrorDetail> errors = new ArrayList<>();
        if (request.getPage() < 0) errors.add(new FieldErrorDetail("page", "page phải không âm"));
        if (request.getSize() < 1 || request.getSize() > MAX_PAGE_SIZE)
            errors.add(new FieldErrorDetail("size", "size phải từ 1 đến 100"));
        if (!errors.isEmpty()) throw new EnrollmentException(ErrorCode.VALIDATION_ERROR, errors);
        if (request.getSemesterId() != null) requirePositiveId(request.getSemesterId(), "semesterId");
        if (request.getSubjectId() != null) requirePositiveId(request.getSubjectId(), "subjectId");
        if (request.getModuleClassId() != null) requirePositiveId(request.getModuleClassId(), "moduleClassId");
    }

    private void requirePositiveId(Long identifier, String field) {
        if (identifier == null || identifier <= 0)
            throw new EnrollmentException(
                    ErrorCode.VALIDATION_ERROR, List.of(new FieldErrorDetail(field, "ID phải là số nguyên dương")));
    }
}
