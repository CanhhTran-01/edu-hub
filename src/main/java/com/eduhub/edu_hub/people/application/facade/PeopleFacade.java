package com.eduhub.edu_hub.people.application.facade;

import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.application.facade.dto.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PeopleFacade {
    /** Thông tin hiển thị sau xác thực; không tải tài khoản hoặc tạo hồ sơ khi đăng nhập. */
    interface LoginProfile {
        Optional<String> findStudentFullNameByUserId(Long userId);

        Optional<String> findLecturerFullNameByUserId(Long userId);
    }

    /** Hợp đồng dữ liệu nhập học; không đưa entity/repository của people ra module khác. */
    interface Admission {
        AdmissionCohortInfo requireCohort(Long cohortId);

        void lockCohort(Long cohortId);

        List<AdmissionClassInfo> findClasses(Long majorId, Long cohortId);

        List<AdmissionClassInfo> findClassesByIds(Set<Long> ids);

        AdmissionClassInfo lockClass(Long classId);

        AdmissionClassInfo createClass(AdmissionClassRequest request);

        AdmissionClassInfo configureClass(Long classId, int minimum, int maximum, int reserve);

        List<AdmissionClassPrefix> findClassPrefixes();

        List<AdmissionStudentInfo> findStudentsByCitizenIds(List<String> citizenIds);

        List<AdmissionStudentInfo> findStudentsByIds(Set<Long> studentIds);

        AdmissionStudentRegistrationResult registerStudent(AdmissionStudentRegistration request);
    }

    interface Scheduling {
        List<StudentPlanningResponse> findStudentsForPlanning();

        List<LecturerResponse> findLecturersByIds(Set<Long> ids);
    }
}
