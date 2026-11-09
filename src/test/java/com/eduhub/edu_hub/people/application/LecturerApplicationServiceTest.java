package com.eduhub.edu_hub.people.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.api.dto.response.UserAccountEmailResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.LecturerSearchRequest;
import com.eduhub.edu_hub.people.application.service.LecturerApplicationService;
import com.eduhub.edu_hub.people.domain.enums.LecturerStatus;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.people.domain.repository.LecturerRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import tools.jackson.databind.json.JsonMapper;

class LecturerApplicationServiceTest {
    private final LecturerRepository lecturerRepository = mock(LecturerRepository.class);
    private final IdentityFacade identityFacade = mock(IdentityFacade.class);
    private final LecturerApplicationService lecturerApplicationService =
            new LecturerApplicationService(lecturerRepository, identityFacade);

    @Test
    void searchGetsCurrentAccountEmailsInOneBatchAndKeepsUnlinkedProfiles() {
        Lecturer firstLecturer = lecturer(1L, 101L);
        Lecturer secondLecturer = lecturer(2L, 102L);
        Lecturer unlinkedLecturer = lecturer(3L, null);
        when(lecturerRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(firstLecturer, secondLecturer, unlinkedLecturer)));
        when(identityFacade.findAccountEmailsByUserIds(Set.of(101L, 102L)))
                .thenReturn(List.of(
                        new UserAccountEmailResponse(101L, "first@example.com"),
                        new UserAccountEmailResponse(102L, null)));

        var response = lecturerApplicationService.search(new LecturerSearchRequest(), 0, 20);

        assertThat(response.getContent())
                .extracting(lecturer -> lecturer.getEmail())
                .containsExactly("first@example.com", null, null);
        verify(identityFacade, times(1)).findAccountEmailsByUserIds(Set.of(101L, 102L));
    }

    @Test
    void detailIncludesEmailFromUserWithoutRequiringAnActiveLecturer() {
        Lecturer lecturer = lecturer(1L, 101L);
        lecturer.setStatus(LecturerStatus.INACTIVE);
        when(lecturerRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(lecturer));
        when(identityFacade.findAccountEmailsByUserIds(Set.of(101L)))
                .thenReturn(List.of(new UserAccountEmailResponse(101L, "current@example.com")));

        var response = lecturerApplicationService.findById(1L);

        assertThat(response.getEmail()).isEqualTo("current@example.com");
        assertThat(response.getStatus()).isEqualTo(LecturerStatus.INACTIVE);
        var profileJson = JsonMapper.builder().build().valueToTree(response);
        assertThat(profileJson.has("accountNotificationStatus")).isFalse();
        assertThat(profileJson.has("accountNotificationError")).isFalse();
    }

    @Test
    void schedulingLookupReadsEmailsForTheWholeSetWithoutDroppingInactiveLecturers() {
        Lecturer firstLecturer = lecturer(1L, 101L);
        Lecturer secondLecturer = lecturer(2L, 102L);
        secondLecturer.setStatus(LecturerStatus.INACTIVE);
        when(lecturerRepository.findAllByIdInAndDeletedFalse(Set.of(1L, 2L)))
                .thenReturn(List.of(firstLecturer, secondLecturer));
        when(identityFacade.findAccountEmailsByUserIds(Set.of(101L, 102L)))
                .thenReturn(List.of(
                        new UserAccountEmailResponse(101L, "first@example.com"),
                        new UserAccountEmailResponse(102L, "second@example.com")));

        var response = lecturerApplicationService.findByIds(Set.of(1L, 2L));

        assertThat(response).hasSize(2);
        assertThat(response.getLast().getStatus()).isEqualTo(LecturerStatus.INACTIVE);
        assertThat(response.getLast().getEmail()).isEqualTo("second@example.com");
        verify(identityFacade, times(1)).findAccountEmailsByUserIds(Set.of(101L, 102L));
    }

    @Test
    void emptyLookupDoesNotCallIdentityOrTheRepository() {
        assertThat(lecturerApplicationService.findByIds(Set.of())).isEmpty();
        verifyNoInteractions(identityFacade, lecturerRepository);
    }

    private Lecturer lecturer(Long lecturerId, Long userId) {
        Lecturer lecturer = new Lecturer();
        lecturer.setId(lecturerId);
        lecturer.setUserId(userId);
        lecturer.setStatus(LecturerStatus.ACTIVE);
        return lecturer;
    }
}
