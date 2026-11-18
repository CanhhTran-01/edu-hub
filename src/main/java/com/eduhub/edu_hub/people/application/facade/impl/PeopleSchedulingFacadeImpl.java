package com.eduhub.edu_hub.people.application.facade.impl;

import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.api.dto.response.TimetableLecturerResponse;
import com.eduhub.edu_hub.people.application.exception.LecturerProfileException;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.StudentPlanningResponse;
import com.eduhub.edu_hub.people.application.service.LecturerApplicationService;
import com.eduhub.edu_hub.people.domain.enums.LecturerStatus;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.people.domain.model.LecturerTeachingSubject;
import com.eduhub.edu_hub.people.domain.repository.LecturerRepository;
import com.eduhub.edu_hub.people.domain.repository.LecturerTeachingSubjectRepository;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeopleSchedulingFacadeImpl implements PeopleFacade.Scheduling {
    private final StudentRepository students;
    private final LecturerApplicationService lecturers;
    private final LecturerRepository lecturerRepository;
    private final LecturerTeachingSubjectRepository teachingSubjectRepository;

    @Override
    public java.util.Optional<Long> findLecturerIdByUserId(Long userId) {
        return lecturerRepository.findLecturerIdByUserId(userId);
    }

    @Override
    public List<TimetableLecturerResponse> findEligibleLecturers(Set<Long> subjectIds) {
        if (subjectIds.isEmpty()) return List.of();
        return toTimetableLecturers(lecturerRepository.findTimetableCandidates(subjectIds, LecturerStatus.ACTIVE));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<TimetableLecturerResponse> lockLecturersForTimetabling(Set<Long> lecturerIds) {
        // Cùng khóa Lecturer gốc với PUT hồ sơ; giữ đến cuối transaction áp dụng lịch.
        var lockedLecturers = lecturerIds.stream()
                .sorted()
                .map(lecturerId -> lecturerRepository
                        .findLecturerProfileByIdForUpdate(lecturerId)
                        .orElseThrow(() -> new LecturerProfileException(ErrorCode.LECTURER_NOT_FOUND)))
                .toList();
        if (lockedLecturers.stream().anyMatch(lecturer -> lecturer.getStatus() != LecturerStatus.ACTIVE))
            throw new LecturerProfileException(ErrorCode.LECTURER_NOT_AVAILABLE);
        return toTimetableLecturers(lockedLecturers);
    }

    private List<TimetableLecturerResponse> toTimetableLecturers(List<Lecturer> lecturerProfiles) {
        if (lecturerProfiles.isEmpty()) return List.of();
        var teachingLinksByLecturerId = teachingSubjectRepository
                .findByLecturerIdInAndDeletedFalseOrderByLecturerIdAscSubjectIdAsc(
                        lecturerProfiles.stream().map(Lecturer::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        LecturerTeachingSubject::getLecturerId,
                        Collectors.mapping(
                                LecturerTeachingSubject::getSubjectId, Collectors.toCollection(TreeSet::new))));
        return lecturerProfiles.stream()
                .map(lecturer -> new TimetableLecturerResponse(
                        lecturer.getId(),
                        lecturer.getFullName(),
                        teachingLinksByLecturerId.getOrDefault(lecturer.getId(), new TreeSet<>())))
                .toList();
    }

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
