package com.eduhub.edu_hub.scheduling.application;

import com.eduhub.edu_hub.scheduling.api.dto.ModuleClassResponse;
import com.eduhub.edu_hub.scheduling.api.dto.SemesterResponse;
import com.eduhub.edu_hub.scheduling.api.dto.TimetableSlot;
import java.util.List;
import java.util.Optional;

public interface SchedulingFacade {

    Optional<ModuleClassResponse> findModuleClassById(Long moduleClassId);

    List<ModuleClassResponse> findModuleClassesBySemester(Long semesterId);

    List<ModuleClassResponse> findModuleClassesByLecturerAndSemester(Long lecturerId, Long semesterId);

    /** Kiểm tra còn chỗ không */
    boolean hasAvailableSlot(Long moduleClassId);

    /** Tăng / giảm sĩ số (gọi từ enrollment) */
    void increaseCurrentSize(Long moduleClassId);

    void decreaseCurrentSize(Long moduleClassId);

    /** Lấy thời khóa biểu */
    List<TimetableSlot> getStudentTimetable(Long studentId, Long semesterId);

    List<TimetableSlot> getLecturerTimetable(Long lecturerId, Long semesterId);

    Optional<SemesterResponse> findSemesterById(Long semesterId);

    boolean isSemesterRegistrationOpen(Long semesterId);

    boolean isGradeLocked(Long semesterId);
}
