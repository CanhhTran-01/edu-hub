package com.eduhub.edu_hub.people.api.mapper;

import com.eduhub.edu_hub.people.api.dto.response.StudentResponse;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Student;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Dựng response từ hồ sơ và tên ngành đã tải; không truy vấn DB hoặc gọi facade trong mapper. */
@Component
public class StudentResponseMapper {
    public StudentResponse toStudentResponse(Student student, Map<Long, String> majorNamesById, String accountEmail) {
        AdministrativeClass administrativeClass = student.getAdministrativeClass();
        return StudentResponse.builder()
                .id(student.getId())
                .studentCode(student.getStudentCode())
                .fullName(student.getFullName())
                .dateOfBirth(student.getDateOfBirth())
                .email(accountEmail)
                .phone(student.getPhone())
                .citizenId(student.getCitizenId())
                .className(administrativeClass == null ? null : administrativeClass.getClassName())
                .majorName(
                        administrativeClass == null || administrativeClass.getMajor() == null
                                ? null
                                : majorNamesById.get(
                                        administrativeClass.getMajor().getId()))
                .cohortCode(
                        administrativeClass == null
                                ? null
                                : administrativeClass.getCohort().getCohortCode())
                .trainingType(
                        administrativeClass == null
                                ? null
                                : administrativeClass.getTrainingType().name())
                .entranceScore(student.getEntranceScore())
                .targetGpa(student.getTargetGpa())
                .activityStatus(student.getActivityStatus())
                .academicWarningStatus(student.getAcademicWarningStatus())
                .academicWarningReason(student.getAcademicWarningReason())
                .createdAt(student.getCreatedAt())
                .build();
    }
}
