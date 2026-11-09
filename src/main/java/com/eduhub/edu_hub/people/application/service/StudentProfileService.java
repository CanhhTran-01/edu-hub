package com.eduhub.edu_hub.people.application.service;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.MajorSimpleResponse;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.UpdateMyStudentProfileRequest;
import com.eduhub.edu_hub.people.api.dto.response.StudentResponse;
import com.eduhub.edu_hub.people.api.mapper.StudentResponseMapper;
import com.eduhub.edu_hub.people.application.exception.StudentProfileException;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.policy.StudentTargetGpaPolicy;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentProfileService {
    private final IdentityFacade identityFacade;
    private final StudentRepository studentRepository;
    private final CurriculumFacade.ReferenceData curriculumReferenceData;
    private final StudentResponseMapper studentResponseMapper;

    public StudentResponse getMyStudentProfile(Jwt authenticatedJwt) {
        UserInfoResponse currentUser = requireCurrentUser(authenticatedJwt);
        Student student = studentRepository
                .findByUserIdAndDeletedFalse(currentUser.getId())
                .orElseThrow(() -> new StudentProfileException(ErrorCode.STUDENT_NOT_FOUND));
        return mapStudentResponse(student, currentUser.getEmail());
    }

    @Transactional
    public StudentResponse updateMyStudentProfile(Jwt authenticatedJwt, UpdateMyStudentProfileRequest request) {

        UserInfoResponse currentUser = requireCurrentUser(authenticatedJwt);

        // lock row đến hết transaction
        // mọi trường được kiểm tra trước khi thay đổi entity đang quản lý.
        Student student = studentRepository
                .findMyProfileForUpdate(currentUser.getId())
                .orElseThrow(() -> new StudentProfileException(ErrorCode.STUDENT_NOT_FOUND));

        if (request.hasTargetGpa()) {
            StudentTargetGpaPolicy.validateTargetGpa(request.getTargetGpa());
        }

        // Email hiện tại chỉ thuộc User; response hồ sơ ghép giá trị từ Identity, không lưu bản sao ở Student.
        // Facade tham gia transaction hồ sơ; lỗi cập nhật email phải truyền ra để rollback toàn bộ PATCH.
        String accountEmail = currentUser.getEmail();
        if (request.hasEmail()) {
            identityFacade.updateAccountEmail(currentUser.getId(), request.getEmail());
            accountEmail = request.getEmail();
        }

        boolean hasChanges = false;
        if (request.hasTargetGpa() && !hasSameTargetGpa(request.getTargetGpa(), student.getTargetGpa())) {
            student.setTargetGpa(request.getTargetGpa());
            hasChanges = true;
        }
        if (request.hasPhone()) {
            String phone =
                    request.getPhone() == null ? null : request.getPhone().trim();
            if (!Objects.equals(phone, student.getPhone())) {
                student.setPhone(phone);
                hasChanges = true;
            }
        }
        if (hasChanges) {
            studentRepository.save(student);
            // TODO(AUDIT_LOG): Bổ sung lại nhật ký cập nhật hồ sơ cá nhân sinh viên sau.
        }
        return mapStudentResponse(student, accountEmail);
    }

    private UserInfoResponse requireCurrentUser(Jwt authenticatedJwt) {

        // Identity vẫn kiểm tra trạng thái tài khoản/phiên trong DB, không chỉ đọc uid từ JWT.
        if (authenticatedJwt == null) {
            throw new StudentProfileException(ErrorCode.UNAUTHENTICATED);
        }

        // TODO(AUTHORIZATION): Chỉ STUDENT được sửa phone/email/targetGpa của mình khi bật phân quyền.
        //  SYSTEM_ADMIN không được quyền cập nhật này; hiện giữ nguyên việc chưa kiểm tra role.

        return identityFacade.requireCurrentUser(authenticatedJwt);
    }

    private boolean hasSameTargetGpa(BigDecimal requestedTargetGpa, BigDecimal currentTargetGpa) {
        return requestedTargetGpa == null
                ? currentTargetGpa == null
                : currentTargetGpa != null && requestedTargetGpa.compareTo(currentTargetGpa) == 0;
    }

    private StudentResponse mapStudentResponse(Student student, String accountEmail) {
        Map<Long, String> majorNamesById = Map.of();
        if (student.getAdministrativeClass() != null
                && student.getAdministrativeClass().getMajor() != null) {
            Long majorId = student.getAdministrativeClass().getMajor().getId();
            if (majorId != null) {
                majorNamesById = curriculumReferenceData.findMajorsByIds(Set.of(majorId)).stream()
                        .collect(Collectors.toMap(MajorSimpleResponse::id, MajorSimpleResponse::majorName));
            }
        }
        return studentResponseMapper.toStudentResponse(student, majorNamesById, accountEmail);
    }
}
