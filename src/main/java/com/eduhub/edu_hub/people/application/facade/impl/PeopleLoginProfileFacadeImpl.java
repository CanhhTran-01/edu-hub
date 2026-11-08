package com.eduhub.edu_hub.people.application.facade.impl;

import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.repository.LecturerRepository;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeopleLoginProfileFacadeImpl implements PeopleFacade.LoginProfile {
    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;

    @Override
    public Optional<String> findStudentFullNameByUserId(Long userId) {
        return studentRepository.findFullNameByUserId(userId);
    }

    @Override
    public Optional<String> findLecturerFullNameByUserId(Long userId) {
        return lecturerRepository.findFullNameByUserId(userId);
    }
}
