package com.eduhub.edu_hub.academic.application.service;

import com.eduhub.edu_hub.academic.api.dto.request.GpaSimulationRequest;
import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationPreviewResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationSnapshotResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationSnapshotResponse.GradeOptionResponse;
import com.eduhub.edu_hub.academic.application.exception.GpaSimulationException;
import com.eduhub.edu_hub.academic.domain.policy.GpaSimulationPolicy;
import com.eduhub.edu_hub.academic.domain.policy.GpaSimulationPolicy.Attempt;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.enrollment.api.dto.response.CourseGradeScaleResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.StudentLearningHistoryResponse;
import com.eduhub.edu_hub.enrollment.application.facade.EnrollmentFacade;
import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.domain.enums.AccessTokenPurpose;
import com.eduhub.edu_hub.identity.domain.enums.RoleName;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import com.eduhub.edu_hub.scheduling.application.facade.SchedulingFacade;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** Snapshot và preview chỉ đọc trong transaction ngắn; không giữ transaction trong lúc gọi AI. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class StudentGpaSimulationService {
    private final IdentityFacade identityFacade;
    private final PeopleFacade.Academic peopleFacade;
    private final EnrollmentFacade.StudentRecords studentRecords;
    private final EnrollmentFacade.GradeScale gradeScaleFacade;
    private final CurriculumFacade.Academic curriculumFacade;
    private final SchedulingFacade.AcademicCalendar academicCalendar;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GpaSimulationSnapshotResponse findMySnapshot(Jwt principal) {
        var snapshot = loadSnapshot(principal);
        var options = snapshot.gradeOptions();
        return GpaSimulationSnapshotResponse.builder()
                .snapshotRef(snapshot.reference())
                .observedAt(LocalDateTime.now(clock))
                .semester(snapshot.semester())
                .historyComplete(snapshot.history().historyComplete())
                .scopeNotice(snapshot.history().scopeNotice())
                .requiredGraduationCredits(snapshot.requiredCredits())
                .graduationTargetGpa(snapshot.graduationTarget())
                .semesterTargetGpa(snapshot.semesterTarget())
                .defaultGrade4(options.get(options.size() / 2).grade4())
                .gradeOptions(options)
                .courses(snapshot.attempts().stream()
                        .filter(attempt -> snapshot.semester().getId().equals(attempt.semesterId()))
                        .map(attempt -> new GpaSimulationSnapshotResponse.CourseResponse(
                                attempt.enrollmentId(),
                                attempt.subjectId(),
                                attempt.subjectCode(),
                                attempt.subjectName(),
                                attempt.credits(),
                                !attempt.hasPublishedGrade(),
                                attempt.actualGrade4(),
                                attempt.actualGradeLetter()))
                        .toList())
                .build();
    }

    public GpaSimulationPreviewResponse previewMyGpa(Jwt principal, GpaSimulationRequest request) {
        if (principal == null) throw new GpaSimulationException(ErrorCode.UNAUTHENTICATED);
        requireRequest(request);
        var snapshot = loadSnapshot(principal);
        if (!snapshot.reference().equals(request.snapshotRef()))
            throw new GpaSimulationException(ErrorCode.GPA_SIMULATION_SNAPSHOT_CHANGED);
        Long currentSemesterId = snapshot.semester().getId();
        Map<Long, BigDecimal> assumptions = new HashMap<>();
        for (var assumption : request.assumptions()) {
            if (assumption == null
                    || assumption.enrollmentId() == null
                    || assumption.enrollmentId() <= 0
                    || assumption.grade4() == null
                    || assumptions.putIfAbsent(
                                    assumption.enrollmentId(),
                                    assumption.grade4().stripTrailingZeros())
                            != null)
                throw invalid("assumptions", "Mỗi lượt học phải có đúng một điểm giả định hợp lệ.");
        }
        var calculation = GpaSimulationPolicy.simulate(
                snapshot.attempts(),
                currentSemesterId,
                assumptions,
                snapshot.gradeOptions().stream()
                        .map(GradeOptionResponse::grade4)
                        .toList(),
                snapshot.history().historyComplete());
        BigDecimal target = snapshot.semesterTarget() == null
                ? null
                : snapshot.semesterTarget().setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal gpa = calculation.semesterGpa();
        return GpaSimulationPreviewResponse.builder()
                .snapshotRef(snapshot.reference())
                .semesterId(currentSemesterId)
                .calculatedAt(LocalDateTime.now(clock))
                .semesterGpa(gpa)
                .cumulativeGpa(calculation.cumulativeGpa())
                .accumulatedCredits(calculation.accumulatedCredits())
                .semesterCredits(calculation.semesterCredits())
                .semesterTargetGpa(target)
                .meetsSemesterTarget(target == null || gpa == null ? null : gpa.compareTo(target) >= 0)
                .semesterTargetGap(
                        target == null || gpa == null
                                ? null
                                : target.subtract(gpa).max(BigDecimal.ZERO).setScale(2, RoundingMode.UNNECESSARY))
                .graduationTargetGpa(snapshot.graduationTarget())
                .requiredGraduationCredits(snapshot.requiredCredits())
                .historyComplete(snapshot.history().historyComplete())
                .scopeNotice(snapshot.history().scopeNotice())
                .courses(snapshot.attempts().stream()
                        .filter(attempt -> currentSemesterId.equals(attempt.semesterId()))
                        .map(attempt -> {
                            BigDecimal grade4 = attempt.hasPublishedGrade()
                                    ? attempt.actualGrade4()
                                    : assumptions.get(attempt.enrollmentId());
                            List<String> letters = attempt.hasPublishedGrade()
                                    ? (attempt.actualGradeLetter() == null
                                            ? List.of()
                                            : List.of(attempt.actualGradeLetter()))
                                    : snapshot.gradeOptions().stream()
                                            .filter(option -> option.grade4().compareTo(grade4) == 0)
                                            .findFirst()
                                            .orElseThrow()
                                            .letters();
                            return new GpaSimulationPreviewResponse.CourseResponse(
                                    attempt.enrollmentId(),
                                    attempt.subjectCode(),
                                    attempt.subjectName(),
                                    attempt.credits(),
                                    attempt.hasPublishedGrade(),
                                    grade4,
                                    letters);
                        })
                        .toList())
                .build();
    }

    private LoadedSnapshot loadSnapshot(Jwt principal) {
        if (principal == null) throw new GpaSimulationException(ErrorCode.UNAUTHENTICATED);
        if (!AccessTokenPurpose.ACCESS.name().equals(principal.getClaim(AccessTokenPurpose.CLAIM_NAME)))
            throw new GpaSimulationException(ErrorCode.FIRST_LOGIN_REQUIRED);
        var user = identityFacade.requireCurrentUser(principal);
        if (!RoleName.STUDENT.name().equals(user.getRole())) throw new GpaSimulationException(ErrorCode.UNAUTHORIZED);
        var student = peopleFacade.findStudentByUserId(user.getId());
        // Phạm vi AI luôn do BE xác định lại; client hoặc hội thoại không được chọn kỳ khác.
        var semester = academicCalendar
                .findCurrentSemester()
                .orElseThrow(() -> invalid("semesterId", "Chưa có kỳ hiện tại để mô phỏng học vụ."));
        var history = studentRecords.findLearningHistory(student.studentId());
        var semestersById = academicCalendar
                .findSemestersByIds(history.enrollments().stream()
                        .map(StudentLearningHistoryResponse.LearningRecordResponse::semesterId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(SemesterResponse::getId, value -> value));
        semestersById.put(semester.getId(), semester);
        var attempts = new ArrayList<Attempt>();
        for (var enrollment : history.enrollments()) {
            var enrollmentSemester = semestersById.get(enrollment.semesterId());
            if (enrollmentSemester == null
                    || enrollmentSemester.getStartDate() == null
                    || semester.getStartDate() == null)
                throw new GpaSimulationException(ErrorCode.GPA_SIMULATION_DATA_INVALID);
            if (enrollmentSemester.getStartDate().isAfter(semester.getStartDate())) continue;
            boolean published =
                    enrollment.status() == EnrollmentStatus.COMPLETED || enrollment.status() == EnrollmentStatus.FAILED;
            if (published != (enrollment.courseScore4() != null)
                    || (!published && enrollment.gradePublishedAt() != null)
                    || enrollment.credits() <= 0
                    || enrollment.registeredAt() == null)
                throw new GpaSimulationException(ErrorCode.GPA_SIMULATION_DATA_INVALID);
            attempts.add(new Attempt(
                    enrollment.enrollmentId(),
                    enrollment.subjectId(),
                    enrollment.semesterId(),
                    enrollment.subjectCode(),
                    enrollment.subjectName(),
                    enrollment.credits(),
                    enrollment.academicYearStart(),
                    enrollment.semesterNumber(),
                    enrollment.registeredAt(),
                    enrollment.courseScore4(),
                    enrollment.courseScoreLetter(),
                    enrollment.status() == EnrollmentStatus.COMPLETED));
        }
        attempts.sort(Comparator.comparing(Attempt::subjectCode).thenComparing(Attempt::enrollmentId));
        var scale = gradeScaleFacade.findCurrentGradeScale();
        if (scale == null || scale.bands() == null || scale.bands().isEmpty())
            throw new GpaSimulationException(ErrorCode.GPA_SIMULATION_DATA_INVALID);
        var lettersByGrade = new TreeMap<BigDecimal, List<String>>();
        for (var band : scale.bands())
            lettersByGrade
                    .computeIfAbsent(band.grade4(), grade -> new ArrayList<>())
                    .add(band.letter());
        var options = lettersByGrade.entrySet().stream()
                .map(entry -> new GradeOptionResponse(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
        var curriculum = curriculumFacade
                .findCurriculum(student.majorId(), student.cohortId(), student.trainingType())
                .orElse(null);
        Integer requiredCredits = curriculum == null ? null : curriculum.requiredGraduationCredits();
        // Dấu nhận diện gắn với chủ sở hữu và dữ liệu ảnh hưởng phép tính, không phụ thuộc thời điểm đọc/khóa điểm.
        var fingerprint = new Fingerprint(
                student.studentId(),
                semester.getId(),
                semester.getStartDate(),
                semester.getEndDate(),
                List.copyOf(attempts),
                history.historyComplete(),
                history.firstSupportedAdmissionYear(),
                history.scopeNotice(),
                scale,
                student.targetGpa(),
                student.semesterTargetGpa(),
                requiredCredits);
        String reference;
        try {
            reference = HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(objectMapper.writeValueAsString(fingerprint).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new GpaSimulationException(ErrorCode.UNEXPECTED_ERROR, exception);
        }
        return new LoadedSnapshot(
                reference,
                semester,
                history,
                List.copyOf(attempts),
                options,
                student.targetGpa(),
                student.semesterTargetGpa(),
                requiredCredits);
    }

    private void requireRequest(GpaSimulationRequest request) {
        if (request == null) throw invalid("request", "Phải gửi dữ liệu mô phỏng.");
        if (request.snapshotRef() == null || !request.snapshotRef().matches("[0-9a-f]{64}"))
            throw invalid("snapshotRef", "Phải dùng snapshotRef từ snapshot vừa tải.");
        if (request.assumptions() == null || request.assumptions().size() > GpaSimulationRequest.MAXIMUM_ASSUMPTIONS)
            throw invalid("assumptions", "Danh sách giả định không hợp lệ.");
    }

    private GpaSimulationException invalid(String field, String message) {
        return new GpaSimulationException(ErrorCode.VALIDATION_ERROR, field, message);
    }

    private record LoadedSnapshot(
            String reference,
            SemesterResponse semester,
            StudentLearningHistoryResponse history,
            List<Attempt> attempts,
            List<GradeOptionResponse> gradeOptions,
            BigDecimal graduationTarget,
            BigDecimal semesterTarget,
            Integer requiredCredits) {}

    private record Fingerprint(
            Long studentId,
            Long semesterId,
            LocalDate startDate,
            LocalDate endDate,
            List<Attempt> attempts,
            boolean historyComplete,
            int firstSupportedAdmissionYear,
            String scopeNotice,
            CourseGradeScaleResponse scale,
            BigDecimal graduationTarget,
            BigDecimal semesterTarget,
            Integer requiredCredits) {}
}
