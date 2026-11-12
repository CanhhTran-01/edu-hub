package com.eduhub.edu_hub.scheduling.application.service;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.scheduling.api.dto.request.*;
import com.eduhub.edu_hub.scheduling.api.dto.response.*;
import com.eduhub.edu_hub.scheduling.api.mapper.TimetablePlanResponseMapper;
import com.eduhub.edu_hub.scheduling.application.exception.*;
import com.eduhub.edu_hub.scheduling.application.specification.ClassScheduleSpecification;
import com.eduhub.edu_hub.scheduling.domain.enums.*;
import com.eduhub.edu_hub.scheduling.domain.model.*;
import com.eduhub.edu_hub.scheduling.domain.policy.*;
import com.eduhub.edu_hub.scheduling.domain.repository.*;
import com.eduhub.edu_hub.scheduling.infrastructure.config.TimetablingSettings;
import com.eduhub.edu_hub.scheduling.infrastructure.persistence.TimetablePlanCodec;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TimetablePlanApplicationService {
    private static final long WORKER_GRACE_PERIOD_SECONDS = 120;
    private final SemesterRepository semesterRepository;
    private final ModuleClassRepository moduleClassRepository;
    private final ClassScheduleRepository scheduleRepository;
    private final RoomRepository roomRepository;
    private final TimetablePlanRepository planRepository;
    private final TimetableSnapshotService snapshotService;
    private final TimetablePlanExecutionService executionService;
    private final ModuleClassApplicationService moduleClassService;
    private final PeopleFacade.Scheduling peopleFacade;
    private final CurriculumFacade.ClassOpening curriculumFacade;
    private final IdentityFacade identityFacade;
    private final TimetablePlanCodec codec;
    private final TimetablingSettings settings;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;
    private final Clock clock;

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
    public record PlanCreated(Long planId) {}
    public record PlanStopRequested(Long planId) {}

    @Transactional
    public TimetablePlanResponse createPlan(Jwt principal, Long semesterId, CreateTimetablePlanRequest request) {

        requireCurrentUser(principal);

        var semester = lockSemester(semesterId);
        var classes = activeClasses(semesterId);
        requireSchedulingAllowed(semester, classes);

        if (request == null
                || request.getPinnedModuleClassIds() == null
                || request.getPinnedModuleClassIds().stream().anyMatch(id -> id == null || id <= 0))
            throw new TimetableException(ErrorCode.VALIDATION_ERROR);

        executionService.expireInterruptedPlans();

        if (planRepository.existsByStateInAndDeletedFalse(TimetablePlanExecutionService.ACTIVE_STATES))
            throw new TimetableException(ErrorCode.TIMETABLE_SOLVER_BUSY);

        var problem = snapshotService.loadProblem(semester, classes, new TreeSet<>(request.getPinnedModuleClassIds()));
        var plan = TimetablePlan.builder()
                .semesterId(semesterId)
                .state(TimetablePlanState.QUEUED)
                .inputFingerprint(codec.fingerprint(problem))
                .problemJson(codec.write(problem))
                .diagnosticsJson("[]")
                .expiresAt(now().plusSeconds(settings.getSpentLimitSeconds() + WORKER_GRACE_PERIOD_SECONDS))
                .build();
        plan.setCreatedAt(now());
        plan.setUpdatedAt(plan.getCreatedAt());
        planRepository.saveAndFlush(plan);
        // Tác vụ chỉ được gửi cho solver sau commit; rollback tạo phương án không được khởi chạy worker.
        eventPublisher.publishEvent(new PlanCreated(plan.getId()));
        return response(plan);
    }

    @Transactional(readOnly = true)
    public TimetablePlanResponse findPlan(Jwt principal, Long planId) {
        requireCurrentUser(principal);
        return response(requirePlan(planId));
    }

    @Transactional
    public TimetablePlanResponse stopPlan(Jwt principal, Long planId) {
        requireCurrentUser(principal);
        var plan = lockPlan(planId);
        if (plan.getState().isActive()) {
            plan.setState(TimetablePlanState.STOP_REQUESTED);
            plan.setUpdatedAt(now());
            planRepository.flush();
            eventPublisher.publishEvent(new PlanStopRequested(planId));
        }
        return response(plan);
    }

    @Transactional
    public TimetablePlanResponse applyPlan(Jwt principal, Long planId, ApplyTimetablePlanRequest request) {
        requireCurrentUser(principal);
        // Chỉ đọc scalar trước khi chờ khóa, tránh dùng entity đã nằm trong persistence context từ trước.
        Long semesterId = planRepository
                .findSemesterIdById(planId)
                .orElseThrow(() -> new TimetableException(ErrorCode.TIMETABLE_PLAN_NOT_FOUND));
        var semester = lockSemester(semesterId);
        var plan = lockPlan(planId);
        if (plan.getState() == TimetablePlanState.APPLIED) return response(plan);
        if (request == null || request.expectedRevision() == null || request.expectedRevision() != plan.getRevision())
            throw new TimetableException(ErrorCode.TIMETABLE_STALE_PLAN);
        if (!plan.getState().allowsApplying()
                || !Long.valueOf(0).equals(plan.getHardPenalty())
                || plan.getAllocationJson() == null) throw new TimetableException(ErrorCode.TIMETABLE_STATE_CONFLICT);
        var classes = activeClasses(semesterId);
        requireSchedulingAllowed(semester, classes);
        for (var moduleClass : classes)
            moduleClassRepository
                    .findForUpdateByIdAndDeletedFalse(moduleClass.getId())
                    .orElseThrow();
        var allocation = codec.read(plan.getAllocationJson(), TimetableAllocation.class);
        var previousProblem = codec.read(plan.getProblemJson(), TimetableProblem.class);
        // Đồng bộ với PUT Lecturer: khóa giảng viên trước môn; khóa phòng trước kiểm tra và ghi lịch.
        var lecturerIds = allocation.sessions().stream()
                .map(TimetableAllocation.SessionPlacement::lecturerId)
                .collect(Collectors.toCollection(TreeSet::new));
        peopleFacade.lockLecturersForTimetabling(lecturerIds);
        var roomIds = allocation.sessions().stream()
                .map(TimetableAllocation.SessionPlacement::roomId)
                .collect(Collectors.toCollection(TreeSet::new));
        Map<Long, Room> roomsById = new HashMap<>();
        curriculumFacade.requireOpenableSubjects(classes.stream()
                .map(moduleClass -> moduleClass.getSubject().getId())
                .collect(Collectors.toSet()));
        for (Long roomId : roomIds)
            roomsById.put(
                    roomId,
                    roomRepository
                            .findForUpdateByIdAndDeletedFalse(roomId)
                            .orElseThrow(() -> new RoomException(ErrorCode.ROOM_NOT_FOUND)));
        roomsById.values().forEach(room -> entityManager.refresh(room, LockModeType.PESSIMISTIC_WRITE));
        entityManager.flush();
        // Đọc lại nguồn lịch sau khi đã giữ khóa tài nguyên, tránh hai lớp khác nhau cùng chiếm một ca.
        var currentProblem = snapshotService.loadProblem(semester, classes, previousProblem.pinnedModuleClassIds());
        if (!plan.getInputFingerprint().equals(codec.fingerprint(currentProblem)))
            throw new TimetableException(ErrorCode.TIMETABLE_STALE_PLAN);
        var assessment = TimetablePolicy.assess(currentProblem, allocation, true);
        if (!assessment.isFeasible())
            throw new TimetableException(
                    ErrorCode.TIMETABLE_INVALID_ALLOCATION,
                    assessment.violations().stream()
                            .map(message -> new FieldErrorDetail("allocation", message))
                            .toList());
        // Phòng là entity đã khóa: kiểm tra trực tiếp cả trạng thái và capacity, không dùng snapshot cũ.
        var requirementsById = currentProblem.classes().stream()
                .collect(Collectors.toMap(TimetableProblem.ClassRequirement::moduleClassId, Function.identity()));
        for (var session : allocation.sessions()) {
            var room = roomsById.get(session.roomId());
            if (room.isDeleted()
                    || room.getStatus() != RoomStatus.ACTIVE
                    || room.getCapacity()
                            < requirementsById.get(session.moduleClassId()).maxCapacity())
                throw new TimetableException(ErrorCode.TIMETABLE_STALE_PLAN);
        }
        var classesById = classes.stream().collect(Collectors.toMap(ModuleClass::getId, Function.identity()));
        var schedules = scheduleRepository.findAll(
                ClassScheduleSpecification.forModuleClasses(classesById.keySet()), Sort.by("id"));
        schedules.forEach(schedule -> {
            schedule.setDeleted(true);
            schedule.setUpdatedAt(now());
        });
        scheduleRepository.saveAll(schedules);
        List<ClassSchedule> newSchedules = new ArrayList<>();
        for (var session : allocation.sessions()) {
            var moduleClass = classesById.get(session.moduleClassId());
            moduleClass.applyTeachingAssignment(entityManager.getReference(Lecturer.class, session.lecturerId()));
            moduleClass.setUpdatedAt(now());
            var schedule = ClassSchedule.builder()
                    .moduleClass(moduleClass)
                    .room(roomsById.get(session.roomId()))
                    .dayOfWeek(session.date().getDayOfWeek().getValue() + 1)
                    .lessonOrderStart((session.shift() - 1) * 3 + 1)
                    .lessonOrderEnd(session.shift() * 3)
                    .startDate(session.date())
                    .endDate(session.date())
                    .build();
            schedule.setCreatedAt(now());
            schedule.setUpdatedAt(schedule.getCreatedAt());
            newSchedules.add(schedule);
        }
        moduleClassRepository.saveAll(classes);
        scheduleRepository.saveAllAndFlush(newSchedules);
        plan.setState(TimetablePlanState.APPLIED);
        plan.setAppliedAt(now());
        plan.setUpdatedAt(plan.getAppliedAt());
        planRepository.flush();
        // TODO(AUDIT_LOG): Ghi actor, phương án và thay đổi giảng viên/lịch sau commit khi có hạ tầng audit.
        return response(plan);
    }

    @Transactional(readOnly = true)
    public TimetableResponse findTimetable(Jwt principal, Long semesterId) {
        requireCurrentUser(principal);
        semesterRepository
                .findByIdAndDeletedFalse(semesterId)
                .orElseThrow(() -> new SemesterException(ErrorCode.SEMESTER_NOT_FOUND));
        var plan = planRepository
                .findFirstBySemesterIdAndStateAndDeletedFalseOrderByAppliedAtDesc(
                        semesterId, TimetablePlanState.APPLIED)
                .orElse(null);
        var allocation = plan == null ? null : response(plan).allocation();
        return new TimetableResponse(
                semesterId,
                plan == null ? null : plan.getId(),
                allocation == null ? null : allocation.phaseOneStart(),
                allocation == null ? null : allocation.phaseOneEnd(),
                allocation == null ? null : allocation.phaseTwoStart(),
                allocation == null ? null : allocation.phaseTwoEnd(),
                allocation == null ? List.of() : allocation.subjects(),
                moduleClassService.findSemesterModuleClasses(semesterId));
    }

    private List<ModuleClass> activeClasses(Long semesterId) {
        return moduleClassRepository.findAllBySemester_IdOrderByIdAsc(semesterId).stream()
                .filter(moduleClass ->
                        !moduleClass.isDeleted() && moduleClass.getStatus() != ModuleClassStatus.CANCELLED)
                .toList();
    }

    private void requireSchedulingAllowed(Semester semester, List<ModuleClass> classes) {
        SemesterPolicy.requireClassChangesAllowed(semester.getEndDate(), semester.isLocked(), LocalDate.now(clock));
        if (!LocalDate.now(clock).isBefore(semester.getRegistrationStart()))
            throw new TimetableException(ErrorCode.TIMETABLE_REGISTRATION_STARTED);
        if (classes.isEmpty())
            throw new TimetableException(
                    ErrorCode.TIMETABLE_INVALID_INPUT,
                    List.of(new FieldErrorDetail("moduleClasses", "Kỳ chưa có lớp để xếp lịch")));
        if (classes.stream()
                .anyMatch(moduleClass -> moduleClass.getCurrentSize() != 0
                        || (moduleClass.getStatus() != ModuleClassStatus.UNSCHEDULED
                                && moduleClass.getStatus() != ModuleClassStatus.SCHEDULED)))
            throw new TimetableException(ErrorCode.TIMETABLE_STATE_CONFLICT);
    }

    private void requireCurrentUser(Jwt principal) {
        if (principal == null) throw new TimetableException(ErrorCode.UNAUTHENTICATED);
        identityFacade.requireCurrentUser(principal);
        // TODO(AUTHORIZATION): Chỉ ACADEMIC_ADMIN được quản lý thời khóa biểu khi triển khai ma trận quyền.
    }

    private Semester lockSemester(Long semesterId) {
        return semesterRepository
                .findForUpdateByIdAndDeletedFalse(semesterId)
                .orElseThrow(() -> new SemesterException(ErrorCode.SEMESTER_NOT_FOUND));
    }

    private TimetablePlan requirePlan(Long planId) {
        return planRepository
                .findByIdAndDeletedFalse(planId)
                .orElseThrow(() -> new TimetableException(ErrorCode.TIMETABLE_PLAN_NOT_FOUND));
    }

    private TimetablePlan lockPlan(Long planId) {
        return planRepository
                .findForUpdateById(planId)
                .orElseThrow(() -> new TimetableException(ErrorCode.TIMETABLE_PLAN_NOT_FOUND));
    }

    private TimetablePlanResponse response(TimetablePlan plan) {
        return TimetablePlanResponseMapper.toResponse(
                plan,
                codec.read(plan.getProblemJson(), TimetableProblem.class),
                plan.getAllocationJson() == null
                        ? null
                        : codec.read(plan.getAllocationJson(), TimetableAllocation.class),
                Arrays.asList(codec.read(plan.getDiagnosticsJson(), String[].class)));
    }
}
