package com.eduhub.edu_hub.people.application.facade.impl;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.identity.api.dto.request.CreateStudentAccountRequest;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.application.code.AdministrativeClassNames;
import com.eduhub.edu_hub.people.application.code.StudentCodeGenerator;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.*;
import com.eduhub.edu_hub.people.domain.enums.AccountNotificationStatus;
import com.eduhub.edu_hub.people.domain.model.*;
import com.eduhub.edu_hub.people.domain.repository.*;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeopleAdmissionFacadeImpl implements PeopleFacade.Admission {
    private final StudentRepository students;
    private final AdministrativeClassRepository classes;
    private final CohortRepository cohorts;
    private final CurriculumFacade.Majors majors;
    private final StudentCodeGenerator codes;
    private final IdentityFacade identity;
    private final EntityManager entities;

    public AdmissionCohortInfo requireCohort(Long id) {
        Cohort cohort = cohorts.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_STUDENT_CODE_COHORT));
        if (cohort.getCodeSequence() == null || cohort.getCodeSequence() < 1 || cohort.getCodeSequence() > 99)
            throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_COHORT);
        return AdmissionCohortInfo.builder()
                .id(id)
                .cohortCode(cohort.getCohortCode())
                .codeSequence(cohort.getCodeSequence())
                .build();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockCohort(Long id) {
        cohorts.findForUpdate(id).orElseThrow(() -> new BaseException(ErrorCode.INVALID_STUDENT_CODE_COHORT));
    }

    public List<AdmissionClassInfo> findClasses(Long majorId, Long cohortId) {
        // Giữ cả lớp đã xóa để không tái sử dụng số thứ tự định danh cũ.
        return classes.findByMajor_IdAndCohort_IdOrderById(majorId, cohortId).stream()
                .map(this::classInfo)
                .toList();
    }

    public List<AdmissionClassInfo> findClassesByIds(Set<Long> ids) {
        return ids.isEmpty()
                ? List.of()
                : classes.findAllById(ids).stream().map(this::classInfo).toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AdmissionClassInfo lockClass(Long id) {
        return classInfo(lockedClass(id));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AdmissionClassInfo createClass(AdmissionClassRequest request) {
        var major = majors.requireMajor(request.majorId());
        Cohort cohort = cohorts.findByIdAndDeletedFalse(request.cohortId())
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_STUDENT_CODE_COHORT));
        AdministrativeClass value = AdministrativeClass.builder()
                .className(AdministrativeClassNames.create(
                        major.majorName(), cohort.getCodeSequence(), request.sequence()))
                .major(entities.getReference(Major.class, request.majorId()))
                .cohort(cohort)
                .trainingType(TrainingType.CHINH_QUY)
                .minCapacity(request.minimum())
                .maxCapacity(request.maximum())
                .reserveCapacity(request.reserve())
                .build();
        return classInfo(classes.saveAndFlush(value));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AdmissionClassInfo configureClass(Long id, int minimum, int maximum, int reserve) {
        var value = lockedClass(id);
        value.setMinCapacity(minimum);
        value.setMaxCapacity(maximum);
        value.setReserveCapacity(reserve);
        return classInfo(value);
    }

    public List<AdmissionClassPrefix> findClassPrefixes() {
        return majors.findMajorsForClassNaming().stream()
                .map(m -> AdmissionClassPrefix.builder()
                        .majorId(m.id())
                        .majorCode(m.majorCode())
                        .majorName(m.majorName())
                        .prefix(AdministrativeClassNames.prefix(m.majorName()))
                        .build())
                .toList();
    }

    public List<AdmissionStudentInfo> findStudentsByCitizenIds(List<String> ids) {
        return ids.isEmpty()
                ? List.of()
                : students.findByCitizenIdIn(ids).stream()
                        .map(this::studentInfo)
                        .toList();
    }

    public List<AdmissionStudentInfo> findStudentsByIds(Set<Long> ids) {
        return ids.isEmpty()
                ? List.of()
                : students.findAllById(ids).stream().map(this::studentInfo).toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AdmissionStudentRegistrationResult registerStudent(AdmissionStudentRegistration request) {
        AdministrativeClass target = lockedClass(request.classId());
        if (students.countByAdministrativeClass_IdAndDeletedFalse(target.getId())
                >= target.getMaxCapacity() + target.getReserveCapacity())
            throw new BaseException(ErrorCode.CLASS_CAPACITY_EXCEEDED);
        Student student =
                students.findByCitizenIdAndDeletedFalse(request.citizenId()).orElse(null);
        boolean reused = student != null;
        boolean locked = false;
        if (!reused) {
            String code = codes.generate(target, codes.nextSequence(target));
            if (students.existsByStudentCode(code) || identity.existsByUsername(code))
                throw new BaseException(ErrorCode.DUPLICATE_STUDENT_CODE);
            Long userId = identity.createStudentAccountWithoutNotification(
                    new CreateStudentAccountRequest(code, request.email()));
            student = Student.builder()
                    .studentCode(code)
                    .userId(userId)
                    .citizenId(request.citizenId())
                    .fullName(request.fullName())
                    .dateOfBirth(request.dateOfBirth())
                    .gender(request.gender())
                    .email(request.email())
                    .phone(request.phone())
                    .build();
        } else {
            locked = identity.getStudentAccount(student.getUserId()).locked();
        }
        student.setAdministrativeClass(target);
        student.setEntranceScore(request.entranceScore());
        student.setActivityStatus(ActivityStatus.STUDYING);
        student.setAccountNotificationStatus(
                request.email() == null ? AccountNotificationStatus.SKIPPED : AccountNotificationStatus.PENDING);
        // Cùng transaction với hồ sơ nhập học: lỗi ở admission phải rollback cả Student/User/MSSV.
        return AdmissionStudentRegistrationResult.builder()
                .student(studentInfo(students.saveAndFlush(student)))
                .reused(reused)
                .accountLocked(locked)
                .build();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateNotificationStatus(Long id, AccountNotificationStatus status, String error) {
        Student student = students.findById(id).orElseThrow(() -> new BaseException(ErrorCode.STUDENT_NOT_FOUND));
        student.setAccountNotificationStatus(status);
        student.setAccountNotificationError(error);
    }

    private AdministrativeClass lockedClass(Long id) {
        return classes.findByIdForStudentCodeUpdate(id)
                .orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE));
    }

    private AdmissionStudentInfo studentInfo(Student s) {
        return AdmissionStudentInfo.builder()
                .id(s.getId())
                .userId(s.getUserId())
                .studentCode(s.getStudentCode())
                .citizenId(s.getCitizenId())
                .fullName(s.getFullName())
                .deleted(s.isDeleted())
                .build();
    }

    private AdmissionClassInfo classInfo(AdministrativeClass c) {
        var sequence = AdministrativeClassNames.sequence(
                c.getClassName(), c.getCohort().getCodeSequence());
        return AdmissionClassInfo.builder()
                .id(c.getId())
                .className(c.getClassName())
                .majorId(c.getMajor().getId())
                .majorCode(c.getMajor().getMajorCode())
                .majorName(c.getMajor().getMajorName())
                .cohortId(c.getCohort().getId())
                .cohortCode(c.getCohort().getCohortCode())
                .cohortSequence(c.getCohort().getCodeSequence())
                .classSequence(sequence.isPresent() ? sequence.getAsInt() : null)
                .trainingType(c.getTrainingType())
                .minCapacity(c.getMinCapacity())
                .maxCapacity(c.getMaxCapacity())
                .reserveCapacity(c.getReserveCapacity())
                .lastStudentSequence(c.getLastStudentSequence())
                .currentSize(students.countByAdministrativeClass_IdAndDeletedFalse(c.getId()))
                .deleted(c.isDeleted())
                .build();
    }
}
