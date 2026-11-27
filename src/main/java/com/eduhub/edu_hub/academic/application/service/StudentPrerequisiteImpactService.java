package com.eduhub.edu_hub.academic.application.service;

import com.eduhub.edu_hub.academic.api.dto.request.PrerequisiteImpactRequest;
import com.eduhub.edu_hub.academic.api.dto.response.PrerequisiteImpactResponse;
import com.eduhub.edu_hub.academic.api.dto.response.PrerequisiteImpactResponse.*;
import com.eduhub.edu_hub.academic.application.exception.StudentAcademicException;
import com.eduhub.edu_hub.academic.domain.enums.*;
import com.eduhub.edu_hub.academic.domain.policy.PrerequisiteImpactPolicy;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.enrollment.api.dto.response.StudentLearningHistoryResponse.LearningRecordResponse;
import com.eduhub.edu_hub.enrollment.application.facade.EnrollmentFacade;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.domain.enums.AccessTokenPurpose;
import com.eduhub.edu_hub.identity.domain.enums.RoleName;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import com.eduhub.edu_hub.scheduling.application.facade.SchedulingFacade;
import com.eduhub.edu_hub.shared.common.TextsNormalizer;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Đọc CTĐT/lịch sử thật trong cùng snapshot, phân tích độc lập từng môn và không ghi học vụ. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class StudentPrerequisiteImpactService {
    private static final int MAXIMUM_GRAPH_SUBJECTS = 2000;
    private static final int GRAPH_QUERY_BATCH_SIZE = 500;
    private static final int MAXIMUM_DEPENDENT_PATHS = 200;
    private static final int MAXIMUM_CHAIN_LENGTH = 64;
    private final IdentityFacade identityFacade;
    private final PeopleFacade.Academic peopleFacade;
    private final EnrollmentFacade.StudentRecords studentRecords;
    private final CurriculumFacade.Academic curriculumFacade;
    private final SchedulingFacade.AcademicCalendar academicCalendar;
    private final Clock clock;

    public PrerequisiteImpactResponse analyzeMyPrerequisites(Jwt principal, PrerequisiteImpactRequest request) {
        if (principal == null) throw new StudentAcademicException(ErrorCode.UNAUTHENTICATED);
        if (!AccessTokenPurpose.ACCESS.name().equals(principal.getClaim(AccessTokenPurpose.CLAIM_NAME)))
            throw new StudentAcademicException(ErrorCode.FIRST_LOGIN_REQUIRED);
        var user = identityFacade.requireCurrentUser(principal);
        if (!RoleName.STUDENT.name().equals(user.getRole())) throw new StudentAcademicException(ErrorCode.UNAUTHORIZED);
        requireRequest(request);
        var student = peopleFacade.findStudentByUserId(user.getId());
        var history = studentRecords.findLearningHistory(student.studentId());
        var semester = academicCalendar.findCurrentSemester().orElse(null);
        var response = PrerequisiteImpactResponse.builder()
                .scope(request.scope())
                .semester(semester)
                .historyComplete(history.historyComplete())
                .graphComplete(false)
                .courseReferences(request.courseReferences())
                .pendingCourseReferences(List.of())
                .courses(List.of())
                .prerequisiteStatus(AcademicRequirementStatus.INSUFFICIENT_DATA)
                .impactStatus(AcademicRequirementStatus.INSUFFICIENT_DATA)
                .calculatedAt(LocalDateTime.now(clock))
                .scopeNotice(history.scopeNotice());
        if (semester == null || semester.getStartDate() == null)
            return response.status(PrerequisiteAnalysisStatus.DATA_INCOMPLETE)
                    .message("Chưa có kỳ hiện tại khả dụng để phân tích tiên quyết.")
                    .build();

        var currentCourses = history.enrollments().stream()
                .filter(course -> semester.getId().equals(course.semesterId()))
                .sorted(Comparator.comparing(LearningRecordResponse::subjectCode)
                        .thenComparing(LearningRecordResponse::enrollmentId))
                .toList();
        var curriculum = curriculumFacade
                .findCurriculum(student.majorId(), student.cohortId(), student.trainingType())
                .orElse(null);
        Map<Long, SubjectReferenceResponse> subjectsById = new LinkedHashMap<>();
        if (curriculum != null)
            curriculum.semesters().forEach(plannedSemester -> plannedSemester
                    .subjects()
                    .forEach(subject -> subjectsById.put(
                            subject.subjectId(),
                            new SubjectReferenceResponse(
                                    subject.subjectId(), subject.subjectCode(), subject.subjectName()))));
        currentCourses.forEach(course -> subjectsById.put(
                course.subjectId(),
                new SubjectReferenceResponse(course.subjectId(), course.subjectCode(), course.subjectName())));

        var selectedSubjectIds = new LinkedHashSet<Long>();
        if (request.scope() == PrerequisiteAnalysisScope.SEMESTER_OVERVIEW)
            currentCourses.forEach(course -> selectedSubjectIds.add(course.subjectId()));
        else {
            if (request.courseReferences().isEmpty())
                return response.status(PrerequisiteAnalysisStatus.NEEDS_CLARIFICATION)
                        .message("Bạn muốn phân tích mã hoặc tên môn nào trong CTĐT/kỳ hiện tại?")
                        .build();
            for (String reference : request.courseReferences()) {
                String normalizedReference = TextsNormalizer.normalizeKey(reference);
                var matches = subjectsById.values().stream()
                        .filter(subject -> normalizedReference.equals(
                                        TextsNormalizer.normalizeKey(subject.subjectCode()))
                                || normalizedReference.equals(TextsNormalizer.normalizeKey(subject.subjectName())))
                        .toList();
                if (matches.isEmpty())
                    matches = subjectsById.values().stream()
                            .filter(subject -> TextsNormalizer.normalizeKey(subject.subjectName())
                                    .contains(normalizedReference))
                            .toList();
                if (matches.size() != 1)
                    return response.status(PrerequisiteAnalysisStatus.NEEDS_CLARIFICATION)
                            .pendingCourseReferences(request.courseReferences())
                            .message(
                                    matches.isEmpty()
                                            ? "Chưa đối chiếu được môn '" + reference
                                                    + "' với CTĐT/kỳ hiện tại. Hãy cung cấp mã môn."
                                            : "Có nhiều môn khớp '" + reference + "': "
                                                    + matches.stream()
                                                            .limit(8)
                                                            .map(subject -> subject.subjectCode() + " — "
                                                                    + subject.subjectName())
                                                            .collect(Collectors.joining(", "))
                                                    + ". Bạn muốn chọn môn nào?")
                            .build();
                selectedSubjectIds.add(matches.getFirst().subjectId());
            }
        }

        // Giữ kỳ của cả kết quả đạt đã ẩn để không lấy kết quả cuối kỳ/tương lai mở tiên quyết đầu cùng kỳ.
        var passedSubjectSemesters = studentRecords.findPassedSubjectSemesters(student.studentId());
        var semesterIds = passedSubjectSemesters.stream()
                .map(course -> course.semesterId())
                .collect(Collectors.toSet());
        var semestersById = academicCalendar.findSemestersByIds(semesterIds).stream()
                .collect(Collectors.toMap(SemesterResponse::getId, Function.identity()));
        semestersById.put(semester.getId(), semester);
        Set<Long> passedBeforeSemester = new HashSet<>();
        Set<Long> passedThroughSemester = new HashSet<>();
        boolean historyComplete = history.historyComplete();
        for (var passed : passedSubjectSemesters) {
            var passedSemester = semestersById.get(passed.semesterId());
            if (passedSemester == null || passedSemester.getStartDate() == null) {
                historyComplete = false;
                continue;
            }
            if (passedSemester.getStartDate().isBefore(semester.getStartDate()))
                passedBeforeSemester.add(passed.subjectId());
            if (!passedSemester.getStartDate().isAfter(semester.getStartDate()))
                passedThroughSemester.add(passed.subjectId());
        }
        var graph = loadPrerequisiteGraph(subjectsById);
        boolean graphComplete = curriculum != null
                && graph.complete()
                && PrerequisiteImpactPolicy.isAcyclic(graph.prerequisitesBySubjectId());
        var coursesBySubjectId =
                currentCourses.stream().collect(Collectors.groupingBy(LearningRecordResponse::subjectId));
        var analyses = new ArrayList<CourseImpactResponse>();
        boolean limited = graph.limited();
        for (var subjectId : selectedSubjectIds) {
            checkInterrupted();
            var analysis = PrerequisiteImpactPolicy.analyze(
                    subjectId,
                    graph.prerequisitesBySubjectId(),
                    passedBeforeSemester,
                    passedThroughSemester,
                    historyComplete,
                    graphComplete,
                    MAXIMUM_DEPENDENT_PATHS,
                    MAXIMUM_CHAIN_LENGTH);
            limited |= analysis.limited();
            var currentAttempts = coursesBySubjectId.getOrDefault(subjectId, List.of());
            var currentAttempt = currentAttempts.isEmpty() ? null : currentAttempts.getFirst();
            var paths = analysis.affectedPaths().stream()
                    .map(path -> new DependencyImpactResponse(
                            subjectReference(subjectsById, path.subjectIds().getLast()),
                            path.subjectIds().stream()
                                    .map(pathId -> subjectReference(subjectsById, pathId))
                                    .toList(),
                            path.status()))
                    .toList();
            analyses.add(new CourseImpactResponse(
                    currentAttempt == null ? null : currentAttempt.enrollmentId(),
                    subjectReference(subjectsById, subjectId),
                    currentAttempt != null,
                    currentAttempts.stream().anyMatch(course -> course.courseScore4() != null),
                    passedThroughSemester.contains(subjectId),
                    analysis.prerequisiteStatus(),
                    analysis.prerequisites().stream()
                            .map(requirement -> new PrerequisiteResponse(
                                    subjectReference(subjectsById, requirement.subjectId()),
                                    requirement.passedBeforeSemester()))
                            .toList(),
                    analysis.failureAssumptionApplicable(),
                    analysis.impactStatus(),
                    paths.stream().filter(path -> path.path().size() == 2).toList(),
                    paths.stream().filter(path -> path.path().size() > 2).toList()));
        }
        String notice = Objects.requireNonNullElse(history.scopeNotice(), "");
        if (!graphComplete) notice += " Chưa đủ CTĐT/đồ thị tiên quyết nhất quán để kết luận đầy đủ.";
        if (!historyComplete) notice += " Chưa đủ lịch sử theo mốc kỳ để xác nhận các môn chưa đạt.";
        return response.historyComplete(historyComplete)
                .graphComplete(graphComplete)
                .status(
                        limited
                                ? PrerequisiteAnalysisStatus.SEARCH_LIMIT_REACHED
                                : !graphComplete || !historyComplete
                                        ? PrerequisiteAnalysisStatus.DATA_INCOMPLETE
                                        : PrerequisiteAnalysisStatus.COMPLETED)
                .courses(List.copyOf(analyses))
                .courseReferences(selectedSubjectIds.stream()
                        .map(subjectId ->
                                subjectReference(subjectsById, subjectId).subjectCode())
                        .toList())
                .prerequisiteStatus(combineStatuses(analyses.stream()
                        .map(CourseImpactResponse::prerequisiteStatus)
                        .toList()))
                .impactStatus(combineStatuses(analyses.stream()
                        .map(CourseImpactResponse::impactStatus)
                        .toList()))
                .scopeNotice(notice)
                .calculatedAt(LocalDateTime.now(clock))
                .message(
                        analyses.isEmpty()
                                ? "Kỳ hiện tại chưa có học phần đăng ký để phân tích."
                                : "Mỗi môn được xét độc lập khi không đạt; không gán F đồng thời cho toàn kỳ. "
                                        + "Ảnh hưởng theo quan hệ hiện hành, chưa đủ căn cứ kết luận lùi kỳ hoặc ngày tốt nghiệp.")
                .build();
    }

    private GraphSnapshot loadPrerequisiteGraph(Map<Long, SubjectReferenceResponse> subjectsById) {
        var prerequisitesBySubjectId = new LinkedHashMap<Long, List<Long>>();
        var pendingSubjectIds = new ArrayDeque<>(subjectsById.keySet());
        var scheduledSubjectIds = new HashSet<>(subjectsById.keySet());
        boolean complete = subjectsById.size() <= MAXIMUM_GRAPH_SUBJECTS;
        boolean limited = !complete;
        while (!pendingSubjectIds.isEmpty() && prerequisitesBySubjectId.size() < MAXIMUM_GRAPH_SUBJECTS) {
            checkInterrupted();
            var batch = new LinkedHashSet<Long>();
            while (!pendingSubjectIds.isEmpty()
                    && batch.size() < GRAPH_QUERY_BATCH_SIZE
                    && prerequisitesBySubjectId.size() + batch.size() < MAXIMUM_GRAPH_SUBJECTS)
                batch.add(pendingSubjectIds.removeFirst());
            var responses = curriculumFacade.findPrerequisitesBySubjectIds(batch);
            var returnedIds = new HashSet<Long>();
            for (var subject : responses) {
                if (!batch.contains(subject.subjectId()) || subject.prerequisites() == null) {
                    complete = false;
                    continue;
                }
                returnedIds.add(subject.subjectId());
                var prerequisiteIds = new ArrayList<Long>();
                for (var prerequisite : subject.prerequisites()) {
                    if (prerequisite == null || prerequisite.getId() == null) {
                        complete = false;
                        continue;
                    }
                    prerequisiteIds.add(prerequisite.getId());
                    if (!scheduledSubjectIds.contains(prerequisite.getId())) {
                        if (scheduledSubjectIds.size() >= MAXIMUM_GRAPH_SUBJECTS) {
                            complete = false;
                            limited = true;
                            continue;
                        }
                        scheduledSubjectIds.add(prerequisite.getId());
                        pendingSubjectIds.add(prerequisite.getId());
                        subjectsById.put(
                                prerequisite.getId(),
                                new SubjectReferenceResponse(
                                        prerequisite.getId(),
                                        prerequisite.getSubjectCode(),
                                        prerequisite.getSubjectName()));
                    }
                }
                prerequisitesBySubjectId.put(subject.subjectId(), List.copyOf(prerequisiteIds));
            }
            if (!returnedIds.containsAll(batch)) complete = false;
        }
        if (!pendingSubjectIds.isEmpty()) {
            complete = false;
            limited = true;
        }
        return new GraphSnapshot(prerequisitesBySubjectId, complete, limited);
    }

    private SubjectReferenceResponse subjectReference(
            Map<Long, SubjectReferenceResponse> subjectsById, Long subjectId) {
        return subjectsById.getOrDefault(
                subjectId, new SubjectReferenceResponse(subjectId, null, "Chưa đủ thông tin học phần"));
    }

    private AcademicRequirementStatus combineStatuses(List<AcademicRequirementStatus> statuses) {
        if (statuses.contains(AcademicRequirementStatus.BLOCKED)) return AcademicRequirementStatus.BLOCKED;
        if (statuses.isEmpty() || statuses.contains(AcademicRequirementStatus.INSUFFICIENT_DATA))
            return AcademicRequirementStatus.INSUFFICIENT_DATA;
        return AcademicRequirementStatus.SATISFIED;
    }

    private void requireRequest(PrerequisiteImpactRequest request) {
        if (request == null
                || request.scope() == null
                || request.courseReferences() == null
                || request.courseReferences().size() > PrerequisiteImpactRequest.MAXIMUM_COURSE_REFERENCES
                || request.courseReferences().stream()
                        .anyMatch(reference -> reference == null || reference.isBlank() || reference.length() > 255)
                || (request.scope() == PrerequisiteAnalysisScope.SEMESTER_OVERVIEW
                        && !request.courseReferences().isEmpty()))
            throw new StudentAcademicException(ErrorCode.VALIDATION_ERROR);
    }

    private void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    private record GraphSnapshot(Map<Long, List<Long>> prerequisitesBySubjectId, boolean complete, boolean limited) {}
}
