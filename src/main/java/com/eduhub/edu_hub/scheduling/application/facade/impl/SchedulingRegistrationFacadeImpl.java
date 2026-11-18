package com.eduhub.edu_hub.scheduling.application.facade.impl;

import com.eduhub.edu_hub.scheduling.api.dto.response.*;
import com.eduhub.edu_hub.scheduling.api.mapper.SemesterResponseMapper;
import com.eduhub.edu_hub.scheduling.application.exception.*;
import com.eduhub.edu_hub.scheduling.application.facade.SchedulingFacade;
import com.eduhub.edu_hub.scheduling.application.service.ModuleClassApplicationService;
import com.eduhub.edu_hub.scheduling.application.service.TimetableSnapshotService;
import com.eduhub.edu_hub.scheduling.application.specification.ClassScheduleSpecification;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.policy.TimetablePolicy;
import com.eduhub.edu_hub.scheduling.domain.repository.*;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchedulingRegistrationFacadeImpl implements SchedulingFacade.Registration {
    private final SemesterRepository semesterRepository;
    private final ModuleClassRepository moduleClassRepository;
    private final ModuleClassApplicationService moduleClassService;
    private final ClassScheduleRepository scheduleRepository;
    private final Clock clock;

    @Override
    public Long findSemesterIdByModuleClassId(Long moduleClassId) {
        return moduleClassRepository
                .findSemesterIdByIdAndDeletedFalse(moduleClassId)
                .orElseThrow(() -> new ModuleClassException(ErrorCode.MODULE_CLASS_NOT_FOUND));
    }

    @Override
    public List<ClassScheduleConflictResponse> findScheduleConflicts(Long targetClassId, Set<Long> registeredClassIds) {
        if (registeredClassIds.isEmpty()) return List.of();

        Set<Long> classIds = new HashSet<>(registeredClassIds);
        classIds.add(targetClassId);

        var schedules = scheduleRepository.findAll(ClassScheduleSpecification.notDeletedAndModuleClassIdIn(classIds));
        List<ClassScheduleConflictResponse> conflicts = new ArrayList<>();
        for (var target : schedules) {
            if (!target.getModuleClass().getId().equals(targetClassId)) continue;

            for (var registered : schedules) {
                if (!registeredClassIds.contains(registered.getModuleClass().getId())) continue;

                TimetablePolicy.firstOverlapDate(
                                TimetableSnapshotService.toExistingSession(target),
                                TimetableSnapshotService.toExistingSession(registered))
                        .ifPresent(date -> conflicts.add(new ClassScheduleConflictResponse(
                                registered.getModuleClass().getId(),
                                registered.getModuleClass().getModuleClassCode(),
                                date,
                                Math.max(target.getLessonOrderStart(), registered.getLessonOrderStart()),
                                Math.min(target.getLessonOrderEnd(), registered.getLessonOrderEnd()))));
            }
        }
        return conflicts.stream().distinct().toList();
    }

    @Override
    public SemesterResponse findSemester(Long semesterId) {
        return SemesterResponseMapper.toResponse(
                semesterRepository
                        .findByIdAndDeletedFalse(semesterId)
                        .orElseThrow(() -> new SemesterException(ErrorCode.SEMESTER_NOT_FOUND)),
                LocalDate.now(clock));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public SemesterResponse lockSemester(Long semesterId) {
        // Khóa đọc kỳ cho phép các sinh viên đăng ký song song; công bố/sửa kỳ giữ khóa ghi cùng hàng.
        return SemesterResponseMapper.toResponse(
                semesterRepository
                        .findForRegistrationByIdAndDeletedFalse(semesterId)
                        .orElseThrow(() -> new SemesterException(ErrorCode.SEMESTER_NOT_FOUND)),
                LocalDate.now(clock));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ModuleClassResponse lockModuleClass(Long moduleClassId) {
        moduleClassRepository
                .findForUpdateByIdAndDeletedFalse(moduleClassId)
                .orElseThrow(() -> new ModuleClassException(ErrorCode.MODULE_CLASS_NOT_FOUND));
        return moduleClassService.findModuleClassById(moduleClassId);
    }

    @Override
    public List<ModuleClassResponse> findOpenClasses(Long semesterId, Set<Long> subjectIds) {
        return moduleClassService.findSemesterModuleClasses(semesterId).stream()
                .filter(moduleClass -> moduleClass.getStatus() == ModuleClassStatus.OPEN
                        && subjectIds.contains(moduleClass.getSubjectId()))
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserveSeat(Long moduleClassId) {
        var moduleClass = moduleClassRepository
                .findForUpdateByIdAndDeletedFalse(moduleClassId)
                .orElseThrow(() -> new ModuleClassException(ErrorCode.MODULE_CLASS_NOT_FOUND));
        if (moduleClass.getStatus() != ModuleClassStatus.OPEN
                || moduleClass.getCurrentSize() >= moduleClass.getMaxCapacity())
            throw new ModuleClassException(ErrorCode.CAPACITY_FULL);
        moduleClass.reserveSeat();
        moduleClass.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseSeat(Long moduleClassId) {
        var moduleClass = moduleClassRepository
                .findForUpdateByIdAndDeletedFalse(moduleClassId)
                .orElseThrow(() -> new ModuleClassException(ErrorCode.MODULE_CLASS_NOT_FOUND));

        if (moduleClass.getCurrentSize() <= 0) throw new ModuleClassException(ErrorCode.ENROLLMENT_DATA_INCOMPLETE);

        moduleClass.releaseSeat();
        moduleClass.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }
}
