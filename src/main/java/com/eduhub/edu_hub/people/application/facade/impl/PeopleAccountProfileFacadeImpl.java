package com.eduhub.edu_hub.people.application.facade.impl;

import com.eduhub.edu_hub.people.api.dto.response.AccountProfileContactResponse;
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
public class PeopleAccountProfileFacadeImpl implements PeopleFacade.AccountProfile {
    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;

    @Override
    public Optional<AccountProfileContactResponse> findStudentContactByUserId(Long userId) {
        return studentRepository.findAccountProfileContactByUserId(userId);
    }

    @Override
    public Optional<AccountProfileContactResponse> findLecturerContactByUserId(Long userId) {
        return lecturerRepository.findAccountProfileContactByUserId(userId);
    }
}
