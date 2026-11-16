package com.eduhub.edu_hub.people.application.facade;

import com.eduhub.edu_hub.people.api.dto.response.AccountProfileContactResponse;
import com.eduhub.edu_hub.people.api.dto.response.EnrollmentStudentResponse;
import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.api.dto.response.StudentRosterProfileResponse;
import com.eduhub.edu_hub.people.api.dto.response.TimetableLecturerResponse;
import com.eduhub.edu_hub.people.application.facade.dto.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PeopleFacade {
    interface Enrollment {
        /** Giữ hồ sơ lịch sử, kể cả đã ẩn/ngừng học, khi lượt đăng ký vẫn thuộc danh sách lớp. */
        List<StudentRosterProfileResponse> findRosterStudentsByIds(Set<Long> studentIds);

        EnrollmentStudentResponse findStudentByUserId(Long userId);

        EnrollmentStudentResponse lockStudentByUserId(Long userId);

        java.util.Map<Long, Integer> findAdmissionYearsByStudentIds(Set<Long> studentIds);
    }
    /** Đọc thông tin liên hệ cho quản trị tài khoản theo User ID; không tải tài khoản từ Identity. */
    interface AccountProfile {
        Optional<AccountProfileContactResponse> findStudentContactByUserId(Long userId);

        Optional<AccountProfileContactResponse> findLecturerContactByUserId(Long userId);
    }

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
        /** Lecturer ID của hồ sơ chưa xóa gắn với User ID đã được consumer xác thực. */
        Optional<Long> findLecturerIdByUserId(Long userId);

        List<TimetableLecturerResponse> findEligibleLecturers(Set<Long> subjectIds);

        List<TimetableLecturerResponse> lockLecturersForTimetabling(Set<Long> lecturerIds);

        List<StudentPlanningResponse> findStudentsForPlanning();

        List<LecturerResponse> findLecturersByIds(Set<Long> ids);
    }
}
