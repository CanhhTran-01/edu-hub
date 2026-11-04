package com.eduhub.edu_hub.people.application.facade.impl;

import com.eduhub.edu_hub.people.api.dto.LecturerResponse;
import com.eduhub.edu_hub.people.api.dto.StudentPlanningResponse;
import com.eduhub.edu_hub.people.application.LecturerApplicationService;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeopleSchedulingFacadeImpl implements PeopleFacade.Scheduling {
    private final StudentRepository students;
    private final LecturerApplicationService lecturers;

    @Override
    public List<StudentPlanningResponse> findStudentsForPlanning() {

        // Chỉ lấy sinh viên chưa xóa mềm và có trạng thái STUDYING (đang học)
        // Sinh viên bảo lưu, thôi học, đã tốt nghiệp không nằm trong diện lập kế hoạch
        return students.findAllByDeletedFalseAndActivityStatus(ActivityStatus.STUDYING).stream()
                .map(student -> {
                    var administrativeClass = student.getAdministrativeClass();
                    var cohort = administrativeClass == null ? null : administrativeClass.getCohort();
                    boolean validClass = administrativeClass != null && !administrativeClass.isDeleted();
                    boolean validCohort = cohort != null && !cohort.isDeleted();
                    var major = administrativeClass == null ? null : administrativeClass.getMajor();

                    // TODO: không join/lọc theo bảng user account, SV chưa có tài khoản vẫn được tính,
                    //  cân nhắc JOIN, ví dụ status nào thì mới không tính, status nào thì vẫn tính

                    // Giữ hồ sơ thiếu mapping không bị lọc bỏ, mà vẫn trả về với các field null
                    // Use-case khác sẽ xử lý, ví dụ nhả PLANNING_DATA_UNAVAILABLE
                    // tránh việc âm thầm bỏ sót sinh viên rồi tính nhu cầu mở lớp thiếu.
                    return new StudentPlanningResponse(
                            student.getId(),
                            student.getStudentCode(),
                            validClass && major != null ? major.getId() : null,
                            validClass && validCohort ? cohort.getId() : null,
                            validClass ? administrativeClass.getTrainingType() : null,
                            validClass && validCohort ? cohort.getStartYear() : null,
                            student.getActivityStatus());
                })
                .toList();
    }

    @Override
    public List<LecturerResponse> findLecturersByIds(Set<Long> ids) {
        return lecturers.findByIds(ids);
    }
}
