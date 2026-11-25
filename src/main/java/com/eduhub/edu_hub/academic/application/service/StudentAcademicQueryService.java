package com.eduhub.edu_hub.academic.application.service;

import com.eduhub.edu_hub.academic.api.dto.response.*;
import com.eduhub.edu_hub.academic.api.dto.response.StudentTranscriptResponse.*;
import com.eduhub.edu_hub.academic.application.exception.StudentAcademicException;
import com.eduhub.edu_hub.academic.domain.policy.AcademicPerformancePolicy;
import com.eduhub.edu_hub.academic.domain.policy.AcademicPerformancePolicy.GradedAttempt;
import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectPrerequisitesResponse;
import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.enrollment.api.dto.response.StudentLearningHistoryResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.StudentLearningHistoryResponse.LearningRecordResponse;
import com.eduhub.edu_hub.enrollment.application.facade.EnrollmentFacade;
import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.domain.enums.AccessTokenPurpose;
import com.eduhub.edu_hub.people.api.dto.response.StudentAcademicProfileResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import com.eduhub.edu_hub.scheduling.application.facade.SchedulingFacade;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class StudentAcademicQueryService {
    private final IdentityFacade identityFacade;
    private final PeopleFacade.Academic peopleFacade;
    private final EnrollmentFacade.StudentRecords studentRecords;
    private final CurriculumFacade.Academic curriculumFacade;
    private final SchedulingFacade.AcademicCalendar academicCalendar;
    private final Clock clock;

    public StudentAcademicOverviewResponse findMyAcademicOverview(Jwt principal) {
        StudentAcademicProfileResponse student = requireStudent(principal);
        StudentLearningHistoryResponse history = studentRecords.findLearningHistory(student.studentId());
        Map<Long, SemesterResponse> semestersById = loadSemesters(history);
        var currentSemester = academicCalendar.findCurrentSemester().orElse(null);
        if (currentSemester != null) semestersById.put(currentSemester.getId(), currentSemester);
        var currentCourses = currentSemester == null
                ? List.<LearningRecordResponse>of()
                : history.enrollments().stream()
                        .filter(enrollment -> enrollment.semesterId().equals(currentSemester.getId()))
                        .toList();
        var currentGrades = gradedAttempts(currentCourses);
        var completedPeriodCourses = history.enrollments().stream()
                .filter(enrollment -> !semestersById
                        .get(enrollment.semesterId())
                        .getStartDate()
                        .isAfter(LocalDate.now(clock)))
                .toList();
        var latestGrades =
                AcademicPerformancePolicy.selectLatestAttemptsBySubject(gradedAttempts(completedPeriodCourses));
        var curriculum = curriculumFacade
                .findCurriculum(student.majorId(), student.cohortId(), student.trainingType())
                .orElse(null);

        // TODO(ACADEMIC_WARNING_EVALUATION): Scheduler sẽ xét/cập nhật cảnh báo theo quy chế khi có đủ nguồn điểm.
        //  GET chỉ trả trạng thái/lý do People đã lưu, không đánh giá hoặc ghi dữ liệu.

        return new StudentAcademicOverviewResponse(
                student,
                currentSemester,
                sortedSemesters(semestersById.values()),
                history.historyComplete() ? AcademicPerformancePolicy.calculateGpa(latestGrades) : null,
                AcademicPerformancePolicy.calculateGpa(currentGrades),
                history.historyComplete() ? AcademicPerformancePolicy.calculateAccumulatedCredits(latestGrades) : null,
                curriculum == null ? null : curriculum.requiredGraduationCredits(),
                currentSemester == null ? null : registeredCredits(currentCourses),
                currentCourses.size(),
                currentGrades.size(),
                history.historyComplete(),
                history.firstSupportedAdmissionYear(),
                history.scopeNotice());
    }

    // Lấy bảng điểm
    public StudentTranscriptResponse findMyTranscript(Jwt principal, Long semesterId) {
        var student = requireStudent(principal);
        if (semesterId != null && semesterId <= 0) throw new StudentAcademicException(ErrorCode.VALIDATION_ERROR);
        var history = studentRecords.findLearningHistory(student.studentId());
        var semestersById = loadSemesters(history);
        if (semesterId != null) semestersById.put(semesterId, academicCalendar.findSemester(semesterId));
        var displayedCourses = history.enrollments().stream()
                .filter(enrollment -> semesterId == null || semesterId.equals(enrollment.semesterId()))
                .toList();
        var coursesBySemester =
                displayedCourses.stream().collect(Collectors.groupingBy(LearningRecordResponse::semesterId));
        var prerequisitesBySubject = curriculumFacade
                .findPrerequisitesBySubjectIds(displayedCourses.stream()
                        .map(LearningRecordResponse::subjectId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(
                        SubjectPrerequisitesResponse::subjectId, SubjectPrerequisitesResponse::prerequisites));
        List<SemesterTranscriptResponse> groups = sortedSemesters(semestersById.values()).stream()
                .filter(semester -> semesterId == null
                        ? coursesBySemester.containsKey(semester.getId())
                        : semesterId.equals(semester.getId()))
                .map(semester -> toSemesterTranscript(
                        semester,
                        coursesBySemester.getOrDefault(semester.getId(), List.of()),
                        history,
                        semestersById,
                        prerequisitesBySubject))
                .toList();
        return new StudentTranscriptResponse(history.historyComplete(), history.scopeNotice(), groups);
    }

    public StudentSemesterRegistrationResponse findMySemsterRegistrations(Jwt principal, Long semesterId) {
        if (principal == null) throw new StudentAcademicException(ErrorCode.UNAUTHENTICATED);
        if (semesterId == null) throw new StudentAcademicException(ErrorCode.VALIDATION_ERROR);
        var transcript = findMyTranscript(principal, semesterId);
        var semesterTranscript = transcript.semesters().getFirst();
        return new StudentSemesterRegistrationResponse(
                semesterTranscript.semester(),
                semesterTranscript.registeredCredits(),
                transcript.historyComplete(),
                transcript.scopeNotice(),
                semesterTranscript.courses());
    }

    private StudentAcademicProfileResponse requireStudent(Jwt principal) {
        if (principal == null) throw new StudentAcademicException(ErrorCode.UNAUTHENTICATED);
        if (!AccessTokenPurpose.ACCESS.name().equals(principal.getClaim(AccessTokenPurpose.CLAIM_NAME)))
            throw new StudentAcademicException(ErrorCode.FIRST_LOGIN_REQUIRED);
        // TODO(AUTHORIZATION): Bổ sung quyền gọi khi triển khai phân quyền; không nhận danh tính từ query/path.
        return peopleFacade.findStudentByUserId(
                identityFacade.requireCurrentUser(principal).getId());
    }

    private Map<Long, SemesterResponse> loadSemesters(StudentLearningHistoryResponse history) {
        var semesterIds = history.enrollments().stream()
                .map(LearningRecordResponse::semesterId)
                .collect(Collectors.toSet());
        var semestersById = academicCalendar.findSemestersByIds(semesterIds).stream()
                .collect(Collectors.toMap(
                        SemesterResponse::getId, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        if (semestersById.size() != semesterIds.size())
            throw new StudentAcademicException(ErrorCode.ENROLLMENT_DATA_INCOMPLETE);
        return semestersById;
    }

    private List<SemesterResponse> sortedSemesters(Collection<SemesterResponse> semesters) {
        return semesters.stream()
                .sorted(Comparator.comparing(SemesterResponse::getStartDate)
                        .reversed()
                        .thenComparing(SemesterResponse::getId, Comparator.reverseOrder()))
                .toList();
    }

    private int registeredCredits(List<LearningRecordResponse> courses) {
        // Lượt đạt/rớt vẫn là học phần đã đăng ký của kỳ; có điểm không làm tổng tín chỉ đăng ký giảm xuống.
        // Nguồn đọc đã loại lượt hủy và xóa mềm, tín chỉ luôn lấy snapshot của từng lượt học.
        return courses.stream().mapToInt(LearningRecordResponse::credits).sum();
    }

    private List<GradedAttempt> gradedAttempts(List<LearningRecordResponse> courses) {
        return courses.stream()
                .filter(enrollment -> enrollment.status() == EnrollmentStatus.COMPLETED
                        || enrollment.status() == EnrollmentStatus.FAILED)
                .filter(enrollment -> enrollment.courseScore4() != null)
                .map(enrollment -> new GradedAttempt(
                        enrollment.enrollmentId(),
                        enrollment.subjectId(),
                        enrollment.credits(),
                        enrollment.academicYearStart(),
                        enrollment.semesterNumber(),
                        enrollment.registeredAt(),
                        enrollment.courseScore4(),
                        enrollment.status() == EnrollmentStatus.COMPLETED))
                .toList();
    }

    private SemesterTranscriptResponse toSemesterTranscript(
            SemesterResponse semester,
            List<LearningRecordResponse> courses,
            StudentLearningHistoryResponse history,
            Map<Long, SemesterResponse> semestersById,
            Map<Long, List<SubjectSimpleResponse>> prerequisitesBySubject) {
        var grades = gradedAttempts(courses);
        // chỉ lọc lịch sử học tập tính đến học kỳ của semesterId, không lấy kì sau
        var historyThroughSemester = history.enrollments().stream()
                .filter(enrollment -> !semestersById
                        .get(enrollment.semesterId())
                        .getStartDate()
                        .isAfter(semester.getStartDate()))
                .toList();
        var latestGrades =
                AcademicPerformancePolicy.selectLatestAttemptsBySubject(gradedAttempts(historyThroughSemester));
        var academicCourses = courses.stream()
                .sorted(Comparator.comparing(LearningRecordResponse::subjectCode)
                        .thenComparing(LearningRecordResponse::enrollmentId))
                .map(enrollment -> toAcademicCourse(enrollment, history, prerequisitesBySubject))
                .toList();
        return new SemesterTranscriptResponse(
                semester,
                AcademicPerformancePolicy.calculateGpa(grades),
                history.historyComplete() ? AcademicPerformancePolicy.calculateGpa(latestGrades) : null,
                history.historyComplete() ? AcademicPerformancePolicy.calculateAccumulatedCredits(latestGrades) : null,
                registeredCredits(courses),
                grades.size(),
                academicCourses);
    }

    private AcademicCourseResponse toAcademicCourse(
            LearningRecordResponse enrollment,
            StudentLearningHistoryResponse history,
            Map<Long, List<SubjectSimpleResponse>> prerequisitesBySubject) {
        var prerequisites = prerequisitesBySubject.getOrDefault(enrollment.subjectId(), List.of()).stream()
                .map(subject -> new PrerequisiteResponse(
                        subject,
                        history.passedSubjectIds().contains(subject.getId())
                                ? Boolean.TRUE
                                : history.historyComplete() ? Boolean.FALSE : null))
                .toList();
        Boolean prerequisitesMet =
                prerequisites.stream().allMatch(prerequisite -> Boolean.TRUE.equals(prerequisite.passed()))
                        ? Boolean.TRUE
                        : history.historyComplete() ? Boolean.FALSE : null;
        return new AcademicCourseResponse(enrollment, prerequisitesMet, prerequisites);
    }
}
