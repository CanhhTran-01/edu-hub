package com.eduhub.edu_hub.people.application.facade.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.people.api.dto.response.StudentRosterProfileResponse;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class PeopleEnrollmentFacadeImplTest {
    private final StudentRepository students = mock(StudentRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final PeopleEnrollmentFacadeImpl facade = new PeopleEnrollmentFacadeImpl(students, entityManager);

    @Test
    void rosterProfilesUseBatchesWithoutLoadingManagedStudentsOrAccounts() {
        var studentIds = LongStream.rangeClosed(1, 501).boxed().collect(Collectors.toSet());
        var firstBatch = LongStream.rangeClosed(1, 500).boxed().toList();
        when(students.findRosterProfilesByIds(firstBatch))
                .thenReturn(List.of(new StudentRosterProfileResponse(1L, "00001", "Sinh viên cũ")));
        when(students.findRosterProfilesByIds(List.of(501L)))
                .thenReturn(List.of(new StudentRosterProfileResponse(501L, "00501", "Sinh viên mới")));
        assertThat(facade.findRosterStudentsByIds(studentIds))
                .extracting(StudentRosterProfileResponse::studentId)
                .containsExactly(1L, 501L);
        verifyNoInteractions(entityManager);
        verify(students).findRosterProfilesByIds(firstBatch);
        verify(students).findRosterProfilesByIds(List.of(501L));
        verifyNoMoreInteractions(students);
    }

    @Test
    void emptyRosterDoesNotQueryPeople() {
        assertThat(facade.findRosterStudentsByIds(Set.of())).isEmpty();
        verifyNoInteractions(students, entityManager);
    }
}
