package com.eduhub.edu_hub.scheduling.application.service;

import com.eduhub.edu_hub.scheduling.api.dto.response.ClassScheduleResponse;
import com.eduhub.edu_hub.scheduling.application.specification.ClassScheduleSpecification;
import com.eduhub.edu_hub.scheduling.domain.enums.TeachingShift;
import com.eduhub.edu_hub.scheduling.domain.repository.ClassScheduleRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đọc lịch theo lô, giữ cả thông tin phòng lịch sử đã ngừng sử dụng. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassScheduleQueryService {
    private final ClassScheduleRepository scheduleRepository;

    public Map<Long, List<ClassScheduleResponse>> findSchedulesByClassIds(Collection<Long> classIds) {
        if (classIds.isEmpty()) return Map.of();
        Map<Long, List<ClassScheduleResponse>> schedulesByClassId = new LinkedHashMap<>();
        for (var schedule : scheduleRepository.findAll(
                ClassScheduleSpecification.forModuleClasses(classIds),
                Sort.by("startDate", "lessonOrderStart", "id"))) {
            var room = schedule.getRoom();
            var teachingShift = TeachingShift.findByLessonRange(
                            schedule.getLessonOrderStart(), schedule.getLessonOrderEnd())
                    .orElse(null);
            var response = ClassScheduleResponse.builder()
                    .id(schedule.getId())
                    .moduleClassId(schedule.getModuleClass().getId())
                    .roomId(room.getId())
                    .roomCode(room.getRoomCode())
                    .buildingCode(room.getBuildingCode())
                    .roomNumber(room.getRoomNumber())
                    .dayOfWeek(schedule.getDayOfWeek())
                    .lessonOrderStart(schedule.getLessonOrderStart())
                    .lessonOrderEnd(schedule.getLessonOrderEnd())
                    .shift(teachingShift == null ? null : teachingShift.getShiftNumber())
                    .startTime(teachingShift == null ? null : teachingShift.getStartTime())
                    .endTime(teachingShift == null ? null : teachingShift.getEndTime())
                    .startDate(schedule.getStartDate())
                    .endDate(schedule.getEndDate())
                    .build();
            schedulesByClassId
                    .computeIfAbsent(response.getModuleClassId(), ignored -> new ArrayList<>())
                    .add(response);
        }
        return schedulesByClassId;
    }
}
