package com.eduhub.edu_hub.scheduling.application.facade;

import com.eduhub.edu_hub.scheduling.api.dto.response.ClassScheduleConflictResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import java.util.List;
import java.util.Set;

public interface SchedulingFacade {

    interface Registration {
        List<ClassScheduleConflictResponse> findScheduleConflicts(Long targetClassId, Set<Long> registeredClassIds);

        Long findSemesterIdByModuleClassId(Long moduleClassId);

        SemesterResponse findSemester(Long semesterId);

        SemesterResponse lockSemester(Long semesterId);

        ModuleClassResponse lockModuleClass(Long moduleClassId);

        List<ModuleClassResponse> findOpenClasses(Long semesterId, Set<Long> subjectIds);

        void reserveSeat(Long moduleClassId);

        void releaseSeat(Long moduleClassId);
    }

    interface TeachingAssignments {
        boolean hasPublishedClassesForSubject(Long subjectId);

        boolean hasUnfinishedScheduledClassesForSubject(Long subjectId);

        Set<Long> findRequiredTeachingSubjectIds(Long lecturerId);
    }

    interface RoomCapacity {
        long countSuitableRooms(int requiredCapacity);
    }
}
