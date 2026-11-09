package com.eduhub.edu_hub.people.application.service;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.MajorSimpleResponse;
import com.eduhub.edu_hub.identity.api.dto.response.UserAccountEmailResponse;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.UpdateStudentRequest;
import com.eduhub.edu_hub.people.api.dto.response.StudentResponse;
import com.eduhub.edu_hub.people.api.mapper.StudentResponseMapper;
import com.eduhub.edu_hub.people.application.exception.StudentProfileException;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.policy.StudentProfileUpdatePolicy;
import com.eduhub.edu_hub.people.domain.policy.StudentTargetGpaPolicy;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Điều phối cập nhật hồ sơ sinh viên theo ID; chuyển lớp/ngành và quản trị tài khoản là các use-case riêng. */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentProfileUpdateService {
    private final StudentRepository studentRepository;
    private final IdentityFacade identityFacade;
    private final CurriculumFacade.ReferenceData curriculumReferenceData;
    private final StudentResponseMapper studentResponseMapper;

    @Transactional
    public StudentResponse updateStudentProfile(Jwt authenticatedJwt, Long studentId, UpdateStudentRequest request) {
        if (authenticatedJwt == null) {
            throw new StudentProfileException(ErrorCode.UNAUTHENTICATED);
        }

        UserInfoResponse currentUser = identityFacade.requireCurrentUser(authenticatedJwt);

        // TODO(AUTHORIZATION): Khi bật phân quyền, chỉ ACADEMIC_ADMIN được sửa hồ sơ quản trị; SYSTEM_ADMIN
        //  không có ngoại lệ cập nhật này. Hiện STUDENT vẫn có thể gọi PUT theo ID để sửa trường bị khóa ở /me.
        //  Đây là giới hạn đã chấp nhận tạm thời; whitelist của PATCH /me không bảo vệ đường PUT này.

        // Kiểm tra toàn bộ dữ liệu trước khi thay đổi entity hoặc gọi facade ghi email.
        String fullName = StudentProfileUpdatePolicy.normalizeFullName(request.getFullName());
        String phone = StudentProfileUpdatePolicy.normalizePhone(request.getPhone());
        String citizenId = StudentProfileUpdatePolicy.normalizeCitizenId(request.getCitizenId());
        String updateReason = StudentProfileUpdatePolicy.normalizeUpdateReason(request.getStatusReason());
        StudentProfileUpdatePolicy.validateDateOfBirth(request.getDateOfBirth(), LocalDate.now(ZoneOffset.UTC));
        StudentTargetGpaPolicy.validateTargetGpa(request.getTargetGpa());

        // Cùng thứ tự với PATCH /me: khóa Student trước User và giữ khóa tới hết transaction.
        Student student = studentRepository
                .findStudentProfileByIdForUpdate(studentId)
                .orElseThrow(() -> new StudentProfileException(ErrorCode.STUDENT_NOT_FOUND));
        if (citizenId != null && studentRepository.existsByCitizenIdAndIdNotAndDeletedFalse(citizenId, studentId)) {
            throw new StudentProfileException(
                    ErrorCode.DUPLICATE_STUDENT_CITIZEN_ID, "citizenId", "CCCD sinh viên đã tồn tại");
        }

        // PUT bỏ field/null giữ nguyên như contract cũ, email được ghi duy nhất vào tài khoản liên kết.
        // Facade tham gia transaction hiện tại; lỗi email phải truyền ra để rollback toàn bộ hồ sơ.
        if (request.getEmail() != null) {
            if (student.getUserId() == null) {
                throw new StudentProfileException(ErrorCode.USER_NOT_FOUND);
            }
            identityFacade.updateAccountEmail(student.getUserId(), request.getEmail());
        }

        // Cập nhật đúng instance đang quản lý; bỏ field và null không xóa dữ liệu ở PUT.
        if (fullName != null) student.setFullName(fullName);
        if (request.getDateOfBirth() != null) student.setDateOfBirth(request.getDateOfBirth());
        if (phone != null) student.setPhone(phone);
        if (citizenId != null) student.setCitizenId(citizenId);
        if (request.getTargetGpa() != null) student.setTargetGpa(request.getTargetGpa());
        Student savedStudent = studentRepository.save(student);

        // TODO(AUDIT_LOG): Lưu actor, Student ID, các trường thay đổi, lý do, thời điểm và kết quả sau commit.
        //  Không ghi mật khẩu/hash/nội dung email hoặc CCCD đầy đủ vào log/audit.
        log.info("Updated student profile: actorId={}, studentId={}, reason={}",
                currentUser.getId(), studentId, updateReason);

        return mapStudentResponse(savedStudent);
    }

    private StudentResponse mapStudentResponse(Student student) {
        AdministrativeClass administrativeClass = student.getAdministrativeClass();
        Map<Long, String> majorNamesById = Map.of();
        if (administrativeClass != null
                && administrativeClass.getMajor() != null
                && administrativeClass.getMajor().getId() != null) {
            majorNamesById =
                    curriculumReferenceData
                            .findMajorsByIds(
                                    Set.of(administrativeClass.getMajor().getId()))
                            .stream()
                            .collect(Collectors.toMap(MajorSimpleResponse::id, MajorSimpleResponse::majorName));
        }
        String accountEmail = student.getUserId() == null
                ? null
                : identityFacade.findAccountEmailsByUserIds(Set.of(student.getUserId())).stream()
                        .findFirst()
                        .map(UserAccountEmailResponse::email)
                        .orElse(null);
        return studentResponseMapper.toStudentResponse(student, majorNamesById, accountEmail);
    }
}
