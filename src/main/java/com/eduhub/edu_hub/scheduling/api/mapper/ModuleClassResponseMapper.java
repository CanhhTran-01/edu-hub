package com.eduhub.edu_hub.scheduling.api.mapper;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassResponse;
import com.eduhub.edu_hub.scheduling.domain.model.ModuleClass;
import java.util.List;
import java.util.Map;

public final class ModuleClassResponseMapper {
    private ModuleClassResponseMapper() {}

    public static ModuleClassResponse toResponse(
            ModuleClass moduleClass,
            Map<Long, SubjectSimpleResponse> subjectsById,
            Map<Long, String> lecturerNamesById) {
        return toResponse(moduleClass, subjectsById, lecturerNamesById, List.of());
    }

    public static ModuleClassResponse toResponse(
            ModuleClass moduleClass,
            Map<Long, SubjectSimpleResponse> subjectsById,
            Map<Long, String> lecturerNamesById,
            List<com.eduhub.edu_hub.scheduling.api.dto.response.ClassScheduleResponse> schedules) {

        var subject = subjectsById.get(moduleClass.getSubject().getId());
        Long lecturerId = moduleClass.getLecturer() == null
                ? null
                : moduleClass.getLecturer().getId();
        String lecturerName = lecturerId == null ? null : lecturerNamesById.get(lecturerId);

        return ModuleClassResponse.builder()
                .id(moduleClass.getId())
                .semesterId(moduleClass.getSemester().getId())
                .semesterCode(moduleClass.getSemester().getSemesterCode())
                .subjectId(moduleClass.getSubject().getId())
                .subjectCode(subject == null ? null : subject.getSubjectCode())
                .subjectName(subject == null ? null : subject.getSubjectName())
                .credits(subject == null ? 0 : subject.getCredits())
                .lecturerId(lecturerId)
                .lecturerName(lecturerName)
                .classSequence(moduleClass.getClassSequence())
                .moduleClassCode(moduleClass.getModuleClassCode())
                .maxCapacity(moduleClass.getMaxCapacity())
                .currentSize(moduleClass.getCurrentSize())
                .status(moduleClass.getStatus())
                .dataSource("DATABASE")
                .schedules(schedules)
                .build();
    }
}
