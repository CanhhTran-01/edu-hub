package com.eduhub.edu_hub.scheduling.application.facade.impl;

import com.eduhub.edu_hub.scheduling.application.facade.SchedulingFacade;
import com.eduhub.edu_hub.scheduling.application.specification.ClassScheduleSpecification;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.repository.ClassScheduleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeachingAssignmentsFacadeImpl implements SchedulingFacade.TeachingAssignments {
    private final ClassScheduleRepository classScheduleRepository;
    private final Clock clock;

    @Override
    public boolean hasPublishedClassesForSubject(Long subjectId) {
        return classScheduleRepository.exists(ClassScheduleSpecification.unfinishedForSubject(
                        subjectId, LocalDate.now(clock))
                .and((root, query, builder) ->
                        root.get("moduleClass").get("status").in(ModuleClassStatus.OPEN, ModuleClassStatus.CLOSED)));
    }

    @Override
    public boolean hasUnfinishedScheduledClassesForSubject(Long subjectId) {
        // trả về cờ boolean cho biết một môn học có còn lịch học chưa kết thúc hay không
        return classScheduleRepository.exists(
                ClassScheduleSpecification.unfinishedForSubject(subjectId, LocalDate.now(clock)));
    }

    @Override
    public Set<Long> findRequiredTeachingSubjectIds(Long lecturerId) {
        // trả về tập ID các môn học mà giảng viên đang phải dạy (vì còn lịch học chưa kết thúc)
        return classScheduleRepository
                .findAll(
                        ClassScheduleSpecification.unfinishedForLecturer(lecturerId, LocalDate.now(clock)),
                        Sort.by("id"))
                .stream()
                .map(schedule -> schedule.getModuleClass().getSubject().getId())
                .collect(Collectors.toSet());
    }
}
