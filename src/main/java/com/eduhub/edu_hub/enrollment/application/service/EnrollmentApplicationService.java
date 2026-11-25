package com.eduhub.edu_hub.enrollment.application.service;

import static com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey.*;

import com.eduhub.edu_hub.curriculum.api.dto.response.*;
import com.eduhub.edu_hub.curriculum.api.dto.response.CurriculumSubjectResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.enrollment.api.dto.request.RegisterModuleClassRequest;
import com.eduhub.edu_hub.enrollment.api.dto.response.*;
import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.enrollment.domain.model.Enrollment;
import com.eduhub.edu_hub.enrollment.domain.policy.EnrollmentRegistrationPolicy;
import com.eduhub.edu_hub.enrollment.domain.repository.EnrollmentRepository;
import com.eduhub.edu_hub.enrollment.infrastructure.config.EnrollmentRegistrationSettings;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.response.EnrollmentStudentResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.enums.StudentActivityStatus;
import com.eduhub.edu_hub.scheduling.api.dto.response.*;
import com.eduhub.edu_hub.scheduling.application.facade.SchedulingFacade;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.systemconfig.api.dto.response.SystemConfigSnapshotResponse;
import com.eduhub.edu_hub.shared.systemconfig.application.service.SystemConfigService;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnrollmentApplicationService {
    private static final Pattern SEMESTER_CODE = Pattern.compile("^HK_([1-9][0-9]*)_([0-9]{4})_([0-9]{4})$");
    private final EnrollmentRepository enrollmentRepository;
    private final IdentityFacade identityFacade;
    private final PeopleFacade.Enrollment peopleFacade;
    private final CurriculumFacade.Catalog curriculumCatalog;
    private final CurriculumFacade.Registration curriculumRegistration;
    private final SchedulingFacade.Registration schedulingFacade;
    private final EnrollmentRegistrationSettings settings;
    private final SystemConfigService systemConfigService;
    private final Clock clock;

    @Transactional
    public EnrollmentRegistrationOptionsResponse findOptions(Jwt principal, Long semesterId) {

        // TODO: giai đoạn này chỉ là đăng ký môn lần đầu. Học lại và học cải thiện là nghiệp vụ riêng, làm sau.

        Long userId = requireCurrentUser(principal);
        var creditConfiguration = systemConfigService.getSnapshot();

        var student = peopleFacade.findStudentByUserId(userId);
        requireEligibleStudent(student);

        var semester = schedulingFacade.findSemester(semesterId);
        boolean allowed = !Boolean.TRUE.equals(semester.getIsLocked())
                && !today().isBefore(semester.getRegistrationStart())
                && !today().isAfter(semester.getRegistrationEnd());

        var curriculumSubjectIds = curriculumSubjectIds(student);
        var classes = schedulingFacade.findOpenClasses(semesterId, curriculumSubjectIds);

        var history = enrollmentRepository.findAllByStudentIdOrderByRegisteredAtAscIdAsc(student.studentId());
        var passedSubjectIds = EnrollmentRegistrationPolicy.passedSubjectIds(history);
        int registeredCredits = EnrollmentRegistrationPolicy.registeredCredits(history, semesterId);

        var subjectsById = curriculumRegistration
                .requireRegistrationSubjects(
                        classes.stream().map(ModuleClassResponse::getSubjectId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(SubjectResponse::getId, subject -> subject));

        return new EnrollmentRegistrationOptionsResponse(
                semesterId,
                allowed,
                registeredCredits,
                creditConfiguration.getInteger(ENROLLMENT_MINIMUM_CREDITS),
                creditConfiguration.getInteger(ENROLLMENT_MAXIMUM_CREDITS),
                warnings(registeredCredits, creditConfiguration),
                scope(),
                classes.stream()
                        .map(moduleClass -> {
                            var missing = missingPrerequisites(
                                    subjectsById.get(moduleClass.getSubjectId()), passedSubjectIds);
                            return new EnrollmentRegistrationOptionsResponse.RegistrationClassResponse(
                                    moduleClass, missing.isEmpty(), missing);
                        })
                        .toList(),
                history.stream()
                        .filter(enrollment -> enrollment.getSemesterId().equals(semesterId))
                        .map(enrollment -> toResponse(enrollment, student))
                        .toList());
    }

    @Transactional
    public EnrollmentRegistrationResponse register(Jwt principal, RegisterModuleClassRequest request) {
        Long userId = requireCurrentUser(principal);
        var creditConfiguration = systemConfigService.getSnapshot();

        if (request == null || request.getModuleClassId() == null || request.getModuleClassId() <= 0)
            throw new EnrollmentException(ErrorCode.VALIDATION_ERROR);

        Long semesterId = schedulingFacade.findSemesterIdByModuleClassId(request.getModuleClassId());

        // Không tải Student/ModuleClass trước khi chờ khóa: kỳ (đọc) → sinh viên → lớp → môn.
        var semester = schedulingFacade.lockSemester(semesterId);
        requireRegistrationWindow(semester);
        var student = peopleFacade.lockStudentByUserId(userId);
        requireEligibleStudent(student);
        var moduleClass = schedulingFacade.lockModuleClass(request.getModuleClassId());
        requireOpenClass(moduleClass, semesterId);

        var history = enrollmentRepository.findAllByStudentIdOrderByRegisteredAtAscIdAsc(student.studentId());
        var existing = history.stream()
                .filter(enrollment -> !enrollment.isDeleted()
                        && enrollment.getModuleClassId().equals(moduleClass.getId())
                        && enrollment.getStatus() == EnrollmentStatus.REGISTERED)
                .findFirst();
        if (existing.isPresent()) return registrationResponse(existing.get(), student, history, creditConfiguration);
        var subject = requireAvailableSubject(student, moduleClass, history);
        requireConflictFreeSchedule(moduleClass, history);

        int registeredCredits = EnrollmentRegistrationPolicy.registeredCredits(history, semesterId);
        EnrollmentRegistrationPolicy.requireWithinCreditLimit(
                registeredCredits, subject.getCredits(), creditConfiguration.getInteger(ENROLLMENT_MAXIMUM_CREDITS));
        if (moduleClass.getCurrentSize() >= moduleClass.getMaxCapacity())
            throw new EnrollmentException(ErrorCode.CAPACITY_FULL);

        var enrollment = createEnrollment(student, moduleClass, semester, semesterId, subject);

        // Hai thao tác tham gia cùng transaction; lỗi unique/commit hoàn tác cả lượt đăng ký lẫn chỗ đã giữ.
        schedulingFacade.reserveSeat(moduleClass.getId());
        enrollmentRepository.saveAndFlush(enrollment);

        var updatedHistory = new ArrayList<>(history);
        updatedHistory.add(enrollment);

        // TODO(AUDIT_LOG): Ghi actor, lượt học và thay đổi sĩ số sau commit khi có hạ tầng audit.
        log.info(
                "Đăng ký học phần: actorId={}, studentId={}, moduleClassId={}, enrollmentId={}",
                userId,
                student.studentId(),
                moduleClass.getId(),
                enrollment.getId());
        return registrationResponse(enrollment, student, updatedHistory, creditConfiguration);
    }

    @Transactional
    public EnrollmentRegistrationResponse drop(Jwt principal, Long enrollmentId) {
        Long userId = requireCurrentUser(principal);
        var creditConfiguration = systemConfigService.getSnapshot();

        // Chỉ đọc danh tính để xác định chủ sở hữu; không tải Enrollment trước khi khóa sinh viên.
        var studentIdentity = peopleFacade.findStudentByUserId(userId);

        Long semesterId = enrollmentRepository
                .findSemesterIdByIdAndStudentId(enrollmentId, studentIdentity.studentId())
                .orElseThrow(() -> new EnrollmentException(ErrorCode.ENROLLMENT_NOT_FOUND));
        var semester = schedulingFacade.lockSemester(semesterId);
        requireRegistrationWindow(semester);

        var student = peopleFacade.lockStudentByUserId(userId);
        requireEligibleStudent(student);

        // Sau khóa mới nạp Enrollment và kiểm tra lại chủ sở hữu
        var enrollment = enrollmentRepository
                .findByIdAndStudentIdAndDeletedFalse(enrollmentId, student.studentId())
                .orElseThrow(() -> new EnrollmentException(ErrorCode.ENROLLMENT_NOT_FOUND));
        if (enrollment.getStatus() == EnrollmentStatus.DROPPED)
            return registrationResponse(
                    enrollment,
                    student,
                    enrollmentRepository.findAllByStudentIdOrderByRegisteredAtAscIdAsc(student.studentId()),
                    creditConfiguration);

        if (enrollment.getStatus() != EnrollmentStatus.REGISTERED)
            throw new EnrollmentException(ErrorCode.ENROLLMENT_CLASS_NOT_OPEN);

        var moduleClass = schedulingFacade.lockModuleClass(enrollment.getModuleClassId());
        requireOpenClass(moduleClass, semesterId);

        enrollment.drop(LocalDateTime.now(clock), currentAuditTimestamp());
        schedulingFacade.releaseSeat(moduleClass.getId());

        enrollmentRepository.flush();

        // TODO(AUDIT_LOG): Ghi hủy đăng ký sau commit; lượt DROPPED vẫn được giữ cho nguồn lịch sử.
        log.info(
                "Hủy đăng ký: actorId={}, studentId={}, moduleClassId={}, enrollmentId={}",
                userId,
                student.studentId(),
                moduleClass.getId(),
                enrollmentId);

        return registrationResponse(
                enrollment,
                student,
                enrollmentRepository.findAllByStudentIdOrderByRegisteredAtAscIdAsc(student.studentId()),
                creditConfiguration);
    }

    private Set<Long> curriculumSubjectIds(EnrollmentStudentResponse student) {
        if (student.majorId() == null || student.cohortId() == null || student.trainingType() == null)
            throw new EnrollmentException(ErrorCode.ENROLLMENT_DATA_INCOMPLETE);

        var curriculum = curriculumCatalog
                .findCurriculum(student.majorId(), student.cohortId(), student.trainingType())
                .orElseThrow(() -> new EnrollmentException(ErrorCode.ENROLLMENT_DATA_INCOMPLETE));

        return curriculum.getSubjects().stream()
                .map(CurriculumSubjectResponse::getSubjectId)
                .collect(Collectors.toSet());
    }

    private List<SubjectSimpleResponse> missingPrerequisites(SubjectResponse subject, Set<Long> passedSubjectIds) {
        return subject.getPrerequisites().stream()
                .filter(prerequisite -> !passedSubjectIds.contains(prerequisite.getId()))
                .toList();
    }

    private void requireEligibleStudent(EnrollmentStudentResponse student) {
        if (student.activityStatus() != StudentActivityStatus.STUDYING)
            throw new EnrollmentException(ErrorCode.ENROLLMENT_STUDENT_INELIGIBLE);
        if (student.admissionYear() == null) throw new EnrollmentException(ErrorCode.ENROLLMENT_DATA_INCOMPLETE);
        if (student.admissionYear() < settings.getFirstSupportedAdmissionYear())
            throw new EnrollmentException(
                    ErrorCode.ENROLLMENT_SCOPE_UNSUPPORTED,
                    List.of(new FieldErrorDetail("cohortId", settings.getScopeNotice())));
        // TODO(ENROLLMENT_LEGACY_HISTORY): sau khi chuyển dữ liệu từ hệ thống cũ; không suy lịch sử rỗng là chưa học.
        // TODO(ACADEMIC_WARNING_EVALUATION): hiện chỉ hồ sơ STUDYING được thao tác.
    }

    private void requireRegistrationWindow(SemesterResponse semester) {
        EnrollmentRegistrationPolicy.requireRegistrationWindow(
                semester.getRegistrationStart(),
                semester.getRegistrationEnd(),
                Boolean.TRUE.equals(semester.getIsLocked()),
                today());
    }

    private void requireOpenClass(ModuleClassResponse moduleClass, Long semesterId) {
        if (!semesterId.equals(moduleClass.getSemesterId()) || moduleClass.getStatus() != ModuleClassStatus.OPEN)
            throw new EnrollmentException(ErrorCode.ENROLLMENT_CLASS_NOT_OPEN);
        if (moduleClass.getLecturerId() == null || moduleClass.getSchedules().isEmpty())
            throw new EnrollmentException(ErrorCode.ENROLLMENT_DATA_INCOMPLETE);
    }

    private Long requireCurrentUser(Jwt principal) {
        if (principal == null) throw new EnrollmentException(ErrorCode.UNAUTHENTICATED);
        // TODO(AUTHORIZATION): Role STUDENT sẽ được kiểm tra khi bật ma trận quyền; ownership luôn kiểm tra ngay.
        return identityFacade.requireCurrentUser(principal).getId();
    }

    private EnrollmentRegistrationResponse registrationResponse(
            Enrollment enrollment,
            EnrollmentStudentResponse student,
            List<Enrollment> history,
            SystemConfigSnapshotResponse creditConfiguration) {
        int credits = EnrollmentRegistrationPolicy.registeredCredits(history, enrollment.getSemesterId());
        return new EnrollmentRegistrationResponse(
                toResponse(enrollment, student),
                credits,
                creditConfiguration.getInteger(ENROLLMENT_MINIMUM_CREDITS),
                creditConfiguration.getInteger(ENROLLMENT_MAXIMUM_CREDITS),
                warnings(credits, creditConfiguration),
                scope());
    }

    private EnrollmentResponse toResponse(Enrollment enrollment, EnrollmentStudentResponse student) {
        return EnrollmentResponse.builder()
                .id(enrollment.getId())
                .studentId(student.studentId())
                .studentCode(student.studentCode())
                .studentName(student.fullName())
                .moduleClassId(enrollment.getModuleClassId())
                .moduleClassCode(enrollment.getModuleClassCode())
                .semesterId(enrollment.getSemesterId())
                .subjectId(enrollment.getSubjectId())
                .subjectCode(enrollment.getSubjectCode())
                .subjectName(enrollment.getSubjectName())
                .credits(enrollment.getCredits())
                .registeredAt(enrollment.getRegisteredAt())
                .droppedAt(enrollment.getDroppedAt())
                .status(enrollment.getStatus().name())
                .componentScore1(enrollment.getComponentScore1())
                .componentScore2(enrollment.getComponentScore2())
                .processScore(enrollment.getProcessScore())
                .finalExamScore(enrollment.getFinalExamScore())
                .courseScore10(enrollment.getCourseScore10())
                .courseScoreLetter(enrollment.getCourseScoreLetter())
                .courseScore4(enrollment.getCourseScore4())
                .gradePublishedAt(enrollment.getGradePublishedAt())
                .gradeLockAt(enrollment.getGradeLockAt())
                .gradeLocked(enrollment.isGradeLocked(LocalDateTime.now(clock)))
                .build();
    }

    private List<String> warnings(int credits, SystemConfigSnapshotResponse creditConfiguration) {
        return EnrollmentRegistrationPolicy.minimumCreditWarnings(
                credits, creditConfiguration.getInteger(ENROLLMENT_MINIMUM_CREDITS));
    }

    private EnrollmentScopeResponse scope() {
        return new EnrollmentScopeResponse(settings.getFirstSupportedAdmissionYear(), settings.getScopeNotice());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private LocalDateTime currentAuditTimestamp() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private SubjectResponse requireAvailableSubject(
            EnrollmentStudentResponse student, ModuleClassResponse moduleClass, List<Enrollment> history) {
        if (!curriculumSubjectIds(student).contains(moduleClass.getSubjectId()))
            throw new EnrollmentException(ErrorCode.ENROLLMENT_SUBJECT_NOT_IN_CURRICULUM);

        EnrollmentRegistrationPolicy.requireSubjectAvailable(moduleClass.getSubjectId(), history);
        var subject = curriculumRegistration
                .requireRegistrationSubjects(Set.of(moduleClass.getSubjectId()))
                .getFirst();
        requirePrerequisites(subject, history);
        return subject;
    }

    private void requirePrerequisites(SubjectResponse subject, List<Enrollment> history) {
        // Chỉ kết quả đã đạt mới thỏa tiên quyết; lượt đang học không được dùng thay kết quả.
        var missing = missingPrerequisites(subject, EnrollmentRegistrationPolicy.passedSubjectIds(history));
        if (!missing.isEmpty()) {
            throw new EnrollmentException(
                    ErrorCode.PREREQUISITE_NOT_MET,
                    missing.stream()
                            .map(prerequisite -> new FieldErrorDetail(
                                    "prerequisites[" + prerequisite.getId() + "]",
                                    "Chưa đạt " + prerequisite.getSubjectCode() + " - "
                                            + prerequisite.getSubjectName()))
                            .toList());
        }
    }

    private void requireConflictFreeSchedule(ModuleClassResponse moduleClass, List<Enrollment> history) {
        var registeredClassIds = history.stream()
                .filter(enrollment -> !enrollment.isDeleted() && enrollment.getStatus() == EnrollmentStatus.REGISTERED)
                .map(Enrollment::getModuleClassId)
                .collect(Collectors.toSet());
        var conflicts = schedulingFacade.findScheduleConflicts(moduleClass.getId(), registeredClassIds);
        if (!conflicts.isEmpty())
            throw new EnrollmentException(
                    ErrorCode.ENROLLMENT_SCHEDULE_CONFLICT,
                    conflicts.stream()
                            .map(conflict -> new FieldErrorDetail(
                                    "moduleClassId",
                                    "Trùng lớp "
                                            + conflict.moduleClassCode() + " ngày " + conflict.date() + ", tiết "
                                            + conflict.lessonOrderStart() + "–" + conflict.lessonOrderEnd()))
                            .toList());
    }

    private Enrollment createEnrollment(
            EnrollmentStudentResponse student,
            ModuleClassResponse moduleClass,
            SemesterResponse semester,
            Long semesterId,
            SubjectResponse subject) {
        var semesterMatcher = SEMESTER_CODE.matcher(semester.getSemesterCode());
        if (!semesterMatcher.matches()) throw new EnrollmentException(ErrorCode.ENROLLMENT_DATA_INCOMPLETE);

        var registeredAt = LocalDateTime.now(clock);
        var enrollment = Enrollment.builder()
                .studentId(student.studentId())
                .moduleClassId(moduleClass.getId())
                .subjectId(subject.getId())
                .semesterId(semesterId)
                .subjectCode(subject.getSubjectCode())
                .subjectName(subject.getSubjectName())
                .moduleClassCode(moduleClass.getModuleClassCode())
                .academicYearStart(Integer.parseInt(semesterMatcher.group(2)))
                .semesterNumber(Integer.parseInt(semesterMatcher.group(1)))
                .credits(subject.getCredits())
                .registeredAt(registeredAt)
                .status(EnrollmentStatus.REGISTERED)
                .build();
        // Mốc đăng ký là giờ Việt Nam; metadata audit được lưu độc lập theo UTC.
        enrollment.setCreatedAt(currentAuditTimestamp());
        enrollment.setUpdatedAt(enrollment.getCreatedAt());

        return enrollment;
    }
}
