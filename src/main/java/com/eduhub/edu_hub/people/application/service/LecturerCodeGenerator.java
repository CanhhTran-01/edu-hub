package com.eduhub.edu_hub.people.application.service;

import com.eduhub.edu_hub.people.application.exception.ProfileCreationException;
import com.eduhub.edu_hub.people.domain.model.Faculty;
import com.eduhub.edu_hub.people.domain.policy.LecturerCodePolicy;
import com.eduhub.edu_hub.people.infrastructure.persistence.LecturerCodeCounterRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LecturerCodeGenerator {
    private final LecturerCodeCounterRepository lecturerCodeCounterRepository;

    @Value("${people.lecturer-code-format:[MaKhoa][NamTuyenDung][SttGV]}")
    private String lecturerCodeFormat = "[MaKhoa][NamTuyenDung][SttGV]";

    public String generateLecturerCode(Faculty faculty, Integer recruitmentYear, int lecturerSequence) {
        return LecturerCodePolicy.generateCode(
                faculty.getFacultyCode(), recruitmentYear, lecturerSequence, lecturerCodeFormat);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int allocateNextLecturerSequence(Faculty faculty, Integer recruitmentYear) {
        LecturerCodePolicy.validateCodeScope(faculty.getFacultyCode(), recruitmentYear);
        LecturerCodePolicy.validateCodeFormat(lecturerCodeFormat);
        return lecturerCodeCounterRepository
                .allocateNextLecturerSequence(
                        faculty.getId(), recruitmentYear, LecturerCodePolicy.MAXIMUM_LECTURER_SEQUENCE)
                .orElseThrow(() -> new ProfileCreationException(ErrorCode.LECTURER_CODE_EXHAUSTED));
    }
}
