package com.eduhub.edu_hub.people.api.mapper;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.domain.model.Faculty;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import java.util.List;
import org.springframework.stereotype.Component;

/** Dùng chung cho tạo/đọc hồ sơ; chỉ ghép email và môn học đã được service tải. */
@Component
public class LecturerResponseMapper {
    public LecturerResponse toLecturerResponse(
            Lecturer lecturer, String accountEmail, List<SubjectSimpleResponse> teachingSubjects) {
        Faculty faculty = lecturer.getFaculty();
        return LecturerResponse.builder()
                .id(lecturer.getId())
                .lecturerCode(lecturer.getLecturerCode())
                .fullName(lecturer.getFullName())
                .email(accountEmail)
                .phone(lecturer.getPhone())
                .dateOfBirth(lecturer.getDateOfBirth())
                .citizenId(lecturer.getCitizenId())
                .facultyId(faculty == null ? null : faculty.getId())
                .facultyName(faculty == null || faculty.isDeleted() ? null : faculty.getFacultyName())
                .status(lecturer.getStatus())
                .recruitmentYear(lecturer.getRecruitmentYear())
                .teachingSubjects(List.copyOf(teachingSubjects))
                .build();
    }
}
