package com.eduhub.edu_hub.scheduling.application.facade;

import java.util.Set;

public interface SchedulingFacade {

    interface TeachingAssignments {
        boolean hasUnfinishedScheduledClassesForSubject(Long subjectId);

        Set<Long> findRequiredTeachingSubjectIds(Long lecturerId);
    }

    interface RoomCapacity {
        long countSuitableRooms(int requiredCapacity);
    }
}
