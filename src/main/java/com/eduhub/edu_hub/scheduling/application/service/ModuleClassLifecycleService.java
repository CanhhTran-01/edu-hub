package com.eduhub.edu_hub.scheduling.application.service;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.scheduling.application.specification.ClassScheduleSpecification;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.TimetableProblem;
import com.eduhub.edu_hub.scheduling.domain.policy.ModuleClassLifecyclePolicy;
import com.eduhub.edu_hub.scheduling.domain.repository.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModuleClassLifecycleService {
    private final SemesterRepository semesterRepository;
    private final ModuleClassRepository moduleClassRepository;
    private final ClassScheduleRepository scheduleRepository;
    private final CurriculumFacade.ClassOpening curriculumFacade;
    private final Clock clock;

    @Transactional
    public void synchronizeSemester(Long semesterId) {
        var semester =
                semesterRepository.findForUpdateByIdAndDeletedFalse(semesterId).orElse(null);
        if (semester == null) return;

        var classes = moduleClassRepository.findAllBySemester_IdOrderByIdAsc(semesterId).stream()
                .filter(moduleClass -> !moduleClass.isDeleted()
                        && (moduleClass.getStatus() == ModuleClassStatus.OPEN
                                || moduleClass.getStatus() == ModuleClassStatus.CLOSED))
                .toList();
        if (classes.isEmpty()) return;

        var subjectsById = curriculumFacade
                .findSubjectsByIds(classes.stream()
                        .map(moduleClass -> moduleClass.getSubject().getId())
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(SubjectSimpleResponse::getId, Function.identity()));
        var schedulesByClassId = scheduleRepository
                .findAll(
                        ClassScheduleSpecification.notDeletedAndModuleClassIdIn(classes.stream()
                                .map(com.eduhub.edu_hub.scheduling.domain.model.ModuleClass::getId)
                                .toList()),
                        Sort.by("id"))
                .stream()
                .map(TimetableSnapshotService::toExistingSession)
                .collect(Collectors.groupingBy(TimetableProblem.ExistingSession::moduleClassId));

        int changedCount = 0; // số lớp đã đổi trạng thái trong lần quét này
        for (var moduleClass : classes) {
            moduleClassRepository
                    .findForUpdateByIdAndDeletedFalse(moduleClass.getId())
                    .orElseThrow();
            var subject = subjectsById.get(moduleClass.getSubject().getId());
            var previousStatus = moduleClass.getStatus(); // lưu trạng thái cũ để log
            var nextStatus = ModuleClassLifecyclePolicy.determineNextStatus(
                    previousStatus,
                    semester.getRegistrationEnd(),
                    LocalDate.now(clock),
                    subject == null ? null : subject.getTotalTeachingPeriods(),
                    schedulesByClassId.getOrDefault(moduleClass.getId(), List.of()),
                    moduleClass.getLecturer() != null);
            if (nextStatus != previousStatus) {
                if (previousStatus == ModuleClassStatus.OPEN) moduleClass.closeRegistration();
                if (nextStatus == ModuleClassStatus.COMPLETED) moduleClass.completeTeaching();
                moduleClass.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
                changedCount++;
                log.debug(
                        "Chuyển trạng thái lớp: moduleClassId={}, {} -> {}",
                        moduleClass.getId(),
                        previousStatus,
                        nextStatus);
            }
        }

        if (changedCount > 0) {
            // TODO(AUDIT_LOG): Ghi các chuyển trạng thái tự động sau commit khi có hạ tầng audit.
            log.info(
                    "Đã đồng bộ trạng thái lớp: semesterId={}, đã quét={}, đã đổi={}",
                    semesterId,
                    classes.size(),
                    changedCount);
        }
    }
}
