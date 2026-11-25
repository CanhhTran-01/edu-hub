package com.eduhub.edu_hub.scheduling.application.service;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.enrollment.application.facade.EnrollmentFacade;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.scheduling.api.dto.request.ModuleClassPublicationRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassPublicationResponse;
import com.eduhub.edu_hub.scheduling.application.exception.*;
import com.eduhub.edu_hub.scheduling.application.specification.ClassScheduleSpecification;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.*;
import com.eduhub.edu_hub.scheduling.domain.policy.*;
import com.eduhub.edu_hub.scheduling.domain.repository.*;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Công bố/thu hồi một tập lớp theo nguyên tắc kiểm tra toàn bộ trước khi thay đổi trạng thái. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModuleClassPublicationService {
    private final SemesterRepository semesterRepository;
    private final ModuleClassRepository moduleClassRepository;
    private final ClassScheduleRepository scheduleRepository;
    private final RoomRepository roomRepository;
    private final TimetableSnapshotService timetableSnapshotService;
    private final ModuleClassApplicationService moduleClassService;
    private final IdentityFacade identityFacade;
    private final PeopleFacade.Scheduling peopleFacade;
    private final CurriculumFacade.ClassOpening curriculumFacade;
    private final EnrollmentFacade.RegistrationUsage enrollmentUsage;
    private final EntityManager entityManager;
    private final Clock clock;

    @Transactional
    public ModuleClassPublicationResponse publish(
            Jwt principal, Long semesterId, ModuleClassPublicationRequest request) {
        Long actorId = requireActor(principal);
        var semester = lockSemester(semesterId);
        var selectedClasses = lockSelectedClasses(semesterId, request, ModuleClassStatus.SCHEDULED);

        requirePublishableClasses(selectedClasses);

        var unpublishedClasses = selectedClasses.stream()
                .filter(moduleClass -> moduleClass.getStatus() == ModuleClassStatus.SCHEDULED)
                .toList();
        if (!unpublishedClasses.isEmpty()) {
            lockPublicationResources(unpublishedClasses);
            List<FieldErrorDetail> errors = new ArrayList<>();
            var problem = timetableSnapshotService.loadProblem(semester, unpublishedClasses, Set.of());
            TimetablePolicy.findOfficialScheduleViolations(problem)
                    .forEach((classId, violations) ->
                            violations.forEach(violation -> errors.add(classError(classId, violation))));
            rejectErrors(errors);
        }

        for (var moduleClass : unpublishedClasses) {
            moduleClass.openRegistration();
            moduleClass.setUpdatedAt(now());
        }
        moduleClassRepository.flush();

        // TODO(AUDIT_LOG): Ghi actor, tập lớp và trạng thái trước/sau sau commit khi có hạ tầng audit.
        log.info(
                "Công bố lớp: actorId={}, semesterId={}, classIds={}",
                actorId,
                semesterId,
                unpublishedClasses.stream().map(ModuleClass::getId).toList());

        return response(semesterId, unpublishedClasses.size(), selectedClasses);
    }

    @Transactional
    public ModuleClassPublicationResponse withdraw(
            Jwt principal, Long semesterId, ModuleClassPublicationRequest request) {
        Long actorId = requireActor(principal);
        lockSemester(semesterId);
        var selectedClasses = lockSelectedClasses(semesterId, request, ModuleClassStatus.OPEN);

        List<FieldErrorDetail> errors = new ArrayList<>();
        for (var moduleClass : selectedClasses) {
            if (moduleClass.getStatus() != ModuleClassStatus.OPEN
                    && moduleClass.getStatus() != ModuleClassStatus.SCHEDULED)
                errors.add(classError(moduleClass.getId(), "Chỉ thu hồi lớp đang mở đăng ký"));
            if (moduleClass.getCurrentSize() != 0 || enrollmentUsage.countActiveRegistrations(moduleClass.getId()) != 0)
                errors.add(classError(
                        moduleClass.getId(), "Lớp còn sinh viên đăng ký; cần hủy đăng ký trước khi thu hồi"));
        }
        rejectErrors(errors);

        int changedClassCount = 0;
        for (var moduleClass : selectedClasses) {
            if (moduleClass.getStatus() == ModuleClassStatus.OPEN) {
                moduleClass.withdrawPublication();
                moduleClass.setUpdatedAt(now());
                changedClassCount++;
            }
        }

        // Thu hồi giữ lịch/giảng viên; xếp lại toàn kỳ vẫn chỉ được thực hiện trước registrationStart.
        moduleClassRepository.flush();

        // TODO(AUDIT_LOG): Ghi kết quả thu hồi sau commit khi có hạ tầng audit.
        log.info(
                "Thu hồi công bố lớp: actorId={}, semesterId={}, classIds={}",
                actorId,
                semesterId,
                selectedClasses.stream().map(ModuleClass::getId).toList());

        return response(semesterId, changedClassCount, selectedClasses);
    }

    private Semester lockSemester(Long semesterId) {
        var semester = semesterRepository
                .findForUpdateByIdAndDeletedFalse(semesterId)
                .orElseThrow(() -> new SemesterException(ErrorCode.SEMESTER_NOT_FOUND));
        SemesterPolicy.requireClassChangesAllowed(semester.getEndDate(), semester.isLocked(), LocalDate.now(clock));
        if (LocalDate.now(clock).isAfter(semester.getRegistrationEnd()))
            throw new ModuleClassException(ErrorCode.REGISTRATION_CLOSED);
        return semester;
    }

    private List<ModuleClass> lockSelectedClasses(
            Long semesterId, ModuleClassPublicationRequest request, ModuleClassStatus allStatus) {
        if (request == null
                || request.getModuleClassIds() == null
                || (request.isSelectAll() == !request.getModuleClassIds().isEmpty())
                || request.getModuleClassIds().stream().anyMatch(classId -> classId == null || classId <= 0))
            throw new ModuleClassException(ErrorCode.VALIDATION_ERROR);

        var classes = moduleClassRepository.findAllBySemester_IdOrderByIdAsc(semesterId);

        Set<Long> selectedIds = request.isSelectAll()
                ? classes.stream()
                        .filter(moduleClass -> !moduleClass.isDeleted() && moduleClass.getStatus() == allStatus)
                        .map(ModuleClass::getId)
                        .collect(Collectors.toCollection(TreeSet::new))
                : new TreeSet<>(request.getModuleClassIds());

        var existingIds = classes.stream()
                .filter(moduleClass -> !moduleClass.isDeleted())
                .map(ModuleClass::getId)
                .collect(Collectors.toSet());

        if (!existingIds.containsAll(selectedIds)) throw new ModuleClassException(ErrorCode.MODULE_CLASS_NOT_FOUND);

        return selectedIds.stream()
                .map(classId -> moduleClassRepository
                        .findForUpdateByIdAndDeletedFalse(classId)
                        .orElseThrow(() -> new ModuleClassException(ErrorCode.MODULE_CLASS_NOT_FOUND)))
                .toList();
    }

    private ModuleClassPublicationResponse response(
            Long semesterId, int changedClassCount, List<ModuleClass> selectedClasses) {
        return new ModuleClassPublicationResponse(
                semesterId,
                changedClassCount,
                selectedClasses.stream()
                        .map(moduleClass -> moduleClassService.findModuleClassById(moduleClass.getId()))
                        .toList());
    }

    private Long requireActor(Jwt principal) {

        // TODO(AUTHORIZATION): Chỉ ACADEMIC_ADMIN được công bố/thu hồi khi áp dụng ma trận quyền.

        if (principal == null) throw new ModuleClassException(ErrorCode.UNAUTHENTICATED);
        return identityFacade.requireCurrentUser(principal).getId();
    }

    private FieldErrorDetail classError(Long classId, String message) {
        return new FieldErrorDetail("moduleClasses[" + classId + "]", message);
    }

    private void rejectErrors(List<FieldErrorDetail> errors) {
        if (!errors.isEmpty())
            throw new ModuleClassConflictException(ErrorCode.MODULE_CLASS_PUBLICATION_INVALID, errors);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private void requirePublishableClasses(List<ModuleClass> selectedClasses) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        for (var moduleClass : selectedClasses) {
            if (moduleClass.getStatus() != ModuleClassStatus.SCHEDULED
                    && moduleClass.getStatus() != ModuleClassStatus.OPEN)
                errors.add(classError(moduleClass.getId(), "Chỉ công bố lớp đã xếp lịch"));

            if (moduleClass.getStatus() == ModuleClassStatus.SCHEDULED && moduleClass.getCurrentSize() != 0)
                errors.add(classError(moduleClass.getId(), "Lớp chưa công bố phải có sĩ số bằng 0"));

            if (moduleClass.getLecturerId() == null)
                errors.add(classError(moduleClass.getId(), "Lớp chưa có giảng viên"));
        }
        rejectErrors(errors);
    }

    private void lockPublicationResources(List<ModuleClass> unpublishedClasses) {
        // Giữ cùng thứ tự tài nguyên với apply: kỳ → lớp → giảng viên → môn → phòng.
        peopleFacade.lockLecturersForTimetabling(unpublishedClasses.stream()
                .map(moduleClass -> moduleClass.getLecturerId())
                .collect(Collectors.toCollection(TreeSet::new)));

        curriculumFacade.requireOpenableSubjects(unpublishedClasses.stream()
                .map(moduleClass -> moduleClass.getSubjectId())
                .collect(Collectors.toSet()));

        var classIds = unpublishedClasses.stream().map(ModuleClass::getId).toList();
        var schedules = scheduleRepository.findAll(ClassScheduleSpecification.notDeletedAndModuleClassIdIn(classIds));
        var sortedRoomIds = schedules.stream()
                .map(schedule -> schedule.getRoom().getId())
                .distinct()
                .sorted()
                .toList();
        for (Long roomId : sortedRoomIds) {
            var room = roomRepository
                    .findForUpdateByIdAndDeletedFalse(roomId)
                    .orElseThrow(() -> new RoomException(ErrorCode.ROOM_NOT_FOUND));

            // Ép nạp lại room từ DB sau khi giữ khóa, tránh dùng bản cache cũ trong persistence context.
            entityManager.refresh(room, LockModeType.PESSIMISTIC_WRITE);
        }
    }
}
