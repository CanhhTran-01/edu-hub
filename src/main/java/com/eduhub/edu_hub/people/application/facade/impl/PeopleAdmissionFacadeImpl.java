package com.eduhub.edu_hub.people.application.facade.impl;

import com.eduhub.edu_hub.curriculum.api.dto.response.MajorSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.identity.api.dto.request.CreateStudentAccountRequest;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.application.code.AdministrativeClassNames;
import com.eduhub.edu_hub.people.application.exception.ProfileCreationException;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.*;
import com.eduhub.edu_hub.people.application.service.StudentCodeGenerator;
import com.eduhub.edu_hub.people.domain.model.*;
import com.eduhub.edu_hub.people.domain.repository.*;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.systemconfig.application.service.SystemConfigService;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import com.eduhub.edu_hub.shared.systemconfig.domain.policy.CodePrefixMappingPolicy;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final CurriculumFacade.ReferenceData curriculumReferenceData;
    private final SystemConfigService systemConfigService;

    public AdmissionCohortInfo requireCohort(Long id) {
        Cohort cohort = cohorts.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ProfileCreationException(ErrorCode.INVALID_STUDENT_CODE_COHORT));
        if (cohort.getCodeSequence() == null || cohort.getCodeSequence() < 1 || cohort.getCodeSequence() > 99)
            throw new ProfileCreationException(ErrorCode.INVALID_STUDENT_CODE_COHORT);
        return AdmissionCohortInfo.builder()
                .id(id)
                .cohortCode(cohort.getCohortCode())
                .codeSequence(cohort.getCodeSequence())
                .build();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockCohort(Long id) {
        cohorts.findForUpdate(id)
                .orElseThrow(() -> new ProfileCreationException(ErrorCode.INVALID_STUDENT_CODE_COHORT));
    }

    public List<AdmissionClassInfo> findClasses(Long majorId, Long cohortId) {
        // Giữ cả lớp đã xóa để không tái sử dụng số thứ tự định danh cũ.
        return loadClassResponses(classes.findByMajorIdAndCohort_IdOrderById(majorId, cohortId));
    }

    public List<AdmissionClassInfo> findClassesByIds(Set<Long> ids) {
        return ids.isEmpty() ? List.of() : loadClassResponses(classes.findAllById(ids));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AdmissionClassInfo lockClass(Long id) {
        return loadClassResponse(lockedClass(id));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AdmissionClassInfo createClass(AdmissionClassRequest request) {
        var major = majors.requireMajor(request.majorId());
        CodePrefixMappingPolicy.requireUnambiguousName(
                major.majorName(),
                majors.findMajorsForClassNaming().stream()
                        .map(candidate -> candidate.majorName())
                        .toList(),
                SystemConfigKey.MAJOR_CODE_PREFIXES);
        Cohort cohort = cohorts.findByIdAndDeletedFalse(request.cohortId())
                .orElseThrow(() -> new ProfileCreationException(ErrorCode.INVALID_STUDENT_CODE_COHORT));
        AdministrativeClass value = AdministrativeClass.builder()
                .className(AdministrativeClassNames.create(
                        major.majorName(),
                        cohort.getCodeSequence(),
                        request.sequence(),
                        systemConfigService.getSnapshot().getCodePrefixes(SystemConfigKey.MAJOR_CODE_PREFIXES)))
                .majorId(request.majorId())
                .cohort(cohort)
                .trainingType(TrainingType.CHINH_QUY)
                .minCapacity(request.minimum())
                .maxCapacity(request.maximum())
                .reserveCapacity(request.reserve())
                .build();
        return mapClassResponse(classes.saveAndFlush(value), major.majorCode(), major.majorName());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AdmissionClassInfo configureClass(Long id, int minimum, int maximum, int reserve) {
        var value = lockedClass(id);
        value.setMinCapacity(minimum);
        value.setMaxCapacity(maximum);
        value.setReserveCapacity(reserve);
        return loadClassResponse(value);
    }

    public List<AdmissionClassPrefix> findClassPrefixes() {
        var majorPrefixes = systemConfigService.getSnapshot().getCodePrefixes(SystemConfigKey.MAJOR_CODE_PREFIXES);
        return majors.findMajorsForClassNaming().stream()
                .map(m -> AdmissionClassPrefix.builder()
                        .majorId(m.id())
                        .majorCode(m.majorCode())
                        .majorName(m.majorName())
                        .prefix(majorPrefixes.get(CodePrefixMappingPolicy.lookupKeyFromName(m.majorName())))
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
            throw new ProfileCreationException(ErrorCode.CLASS_CAPACITY_EXCEEDED);
        Student student =
                students.findByCitizenIdAndDeletedFalse(request.citizenId()).orElse(null);
        boolean reused = student != null;
        boolean locked = false;
        if (!reused) {
            String code = codes.generateStudentCode(target, codes.allocateNextStudentSequence(target));
            if (students.existsByStudentCode(code) || identity.existsByUsername(code))
                throw new ProfileCreationException(ErrorCode.DUPLICATE_STUDENT_CODE);
            Long userId = identity.createStudentAccountWithoutNotification(
                    new CreateStudentAccountRequest(code, request.email()));
            student = Student.builder()
                    .studentCode(code)
                    .userId(userId)
                    .citizenId(request.citizenId())
                    .fullName(request.fullName())
                    .dateOfBirth(request.dateOfBirth())
                    .gender(request.gender())
                    .phone(request.phone())
                    .build();
        } else {
            locked = identity.getStudentAccount(student.getUserId()).locked();
        }
        student.setAdministrativeClass(target);
        student.setEntranceScore(request.entranceScore());
        student.setActivityStatus(ActivityStatus.STUDYING);
        // Cùng transaction với hồ sơ nhập học: lỗi ở admission phải rollback cả Student/User/MSSV.
        return AdmissionStudentRegistrationResult.builder()
                .student(studentInfo(students.saveAndFlush(student)))
                .reused(reused)
                .accountLocked(locked)
                .build();
    }

    private AdministrativeClass lockedClass(Long id) {
        return classes.findByIdForStudentCodeUpdate(id)
                .orElseThrow(() -> new ProfileCreationException(ErrorCode.ADMISSION_CLASS_UNAVAILABLE));
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

    private AdmissionClassInfo loadClassResponse(AdministrativeClass administrativeClass) {
        return loadClassResponses(List.of(administrativeClass)).getFirst();
    }

    private List<AdmissionClassInfo> loadClassResponses(List<AdministrativeClass> administrativeClasses) {
        Set<Long> majorIds = administrativeClasses.stream()
                .map(AdministrativeClass::getMajorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, MajorSimpleResponse> majorsById = majorIds.isEmpty()
                ? Map.of()
                : curriculumReferenceData.findMajorsByIds(majorIds).stream()
                        .collect(Collectors.toMap(MajorSimpleResponse::id, major -> major));
        return administrativeClasses.stream()
                .map(administrativeClass -> {
                    var major = majorsById.get(administrativeClass.getMajorId());
                    if (major == null) throw new ProfileCreationException(ErrorCode.INVALID_STUDENT_CODE_MAJOR);
                    return mapClassResponse(administrativeClass, major.majorCode(), major.majorName());
                })
                .toList();
    }

    private AdmissionClassInfo mapClassResponse(
            AdministrativeClass administrativeClass, String majorCode, String majorName) {
        var sequence = AdministrativeClassNames.sequence(
                administrativeClass.getClassName(),
                administrativeClass.getCohort().getCodeSequence());
        return AdmissionClassInfo.builder()
                .id(administrativeClass.getId())
                .className(administrativeClass.getClassName())
                .majorId(administrativeClass.getMajorId())
                .majorCode(majorCode)
                .majorName(majorName)
                .cohortId(administrativeClass.getCohort().getId())
                .cohortCode(administrativeClass.getCohort().getCohortCode())
                .cohortSequence(administrativeClass.getCohort().getCodeSequence())
                .classSequence(sequence.isPresent() ? sequence.getAsInt() : null)
                .trainingType(administrativeClass.getTrainingType())
                .minCapacity(administrativeClass.getMinCapacity())
                .maxCapacity(administrativeClass.getMaxCapacity())
                .reserveCapacity(administrativeClass.getReserveCapacity())
                .lastStudentSequence(administrativeClass.getLastStudentSequence())
                .currentSize(students.countByAdministrativeClass_IdAndDeletedFalse(administrativeClass.getId()))
                .deleted(administrativeClass.isDeleted())
                .build();
    }
}
