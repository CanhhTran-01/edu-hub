package com.eduhub.edu_hub.people.application.facade;

import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.application.facade.dto.StudentPlanningResponse;
import java.util.List;
import java.util.Set;

public interface PeopleFacade {
    interface Scheduling {
        List<StudentPlanningResponse> findStudentsForPlanning();

        List<LecturerResponse> findLecturersByIds(Set<Long> ids);
    }
}
