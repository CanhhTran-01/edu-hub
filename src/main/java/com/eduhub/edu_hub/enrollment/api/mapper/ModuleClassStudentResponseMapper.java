package com.eduhub.edu_hub.enrollment.api.mapper;

import com.eduhub.edu_hub.enrollment.api.dto.response.ModuleClassStudentResponse;
import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.enrollment.domain.model.Enrollment;
import com.eduhub.edu_hub.people.api.dto.response.StudentRosterProfileResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Ghép lượt học với hồ sơ đã tải cho cả roster và nhập điểm; không truy vấn từ mapper. */
public final class ModuleClassStudentResponseMapper {
    private ModuleClassStudentResponseMapper() {}

    public static List<ModuleClassStudentResponse> toResponses(
            List<Enrollment> enrollments, List<StudentRosterProfileResponse> studentProfiles) {
        var studentsById = studentProfiles.stream()
                .collect(Collectors.toMap(StudentRosterProfileResponse::studentId, Function.identity()));
        return enrollments.stream()
                .map(enrollment -> {
                    var student = studentsById.get(enrollment.getStudentId());
                    // Không âm thầm bỏ sinh viên và xuất một danh sách thiếu khi liên kết hồ sơ bị lỗi.
                    if (student == null || student.studentCode() == null || student.fullName() == null)
                        throw new EnrollmentException(ErrorCode.ENROLLMENT_DATA_INCOMPLETE);
                    return new ModuleClassStudentResponse(
                            enrollment.getId(),
                            student.studentId(),
                            student.studentCode(),
                            student.fullName(),
                            enrollment.getComponentScore1(),
                            enrollment.getComponentScore2(),
                            enrollment.getProcessScore(),
                            enrollment.getNote());
                })
                .sorted(Comparator.comparing(ModuleClassStudentResponse::studentCode)
                        .thenComparing(ModuleClassStudentResponse::enrollmentId))
                .toList();
    }
}
