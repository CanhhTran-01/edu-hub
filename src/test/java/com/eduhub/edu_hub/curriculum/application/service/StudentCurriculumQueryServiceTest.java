package com.eduhub.edu_hub.curriculum.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.api.dto.response.*;
import com.eduhub.edu_hub.curriculum.application.exception.StudentCurriculumException;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.enums.*;
import com.eduhub.edu_hub.curriculum.domain.model.*;
import com.eduhub.edu_hub.curriculum.domain.repository.SubjectPrerequisiteRepository;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.response.EnrollmentStudentResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.enums.StudentActivityStatus;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class StudentCurriculumQueryServiceTest {
    IdentityFacade identityFacade = mock(IdentityFacade.class);
    PeopleFacade.Enrollment peopleFacade = mock(PeopleFacade.Enrollment.class);
    CurriculumFacade.Catalog catalog = mock(CurriculumFacade.Catalog.class);
    SubjectPrerequisiteRepository prerequisiteRepository = mock(SubjectPrerequisiteRepository.class);
    StudentCurriculumQueryService service =
            new StudentCurriculumQueryService(identityFacade, peopleFacade, catalog, prerequisiteRepository);
    Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("student")
            .claim("token_use", "ACCESS")
            .build();

    @Test
    void resolvesProgramFromAuthenticatedProfileAndIncludesTenGroupsIncludingEmptyOnes() {
        when(identityFacade.requireCurrentUser(principal))
                .thenReturn(UserInfoResponse.builder().id(7L).build());
        when(peopleFacade.findStudentByUserId(7L))
                .thenReturn(new EnrollmentStudentResponse(
                        99L,
                        "SV99",
                        "Sinh viên",
                        2L,
                        3L,
                        TrainingType.LIEN_THONG,
                        2026,
                        StudentActivityStatus.STUDYING));
        when(catalog.findCurriculum(2L, 3L, TrainingType.LIEN_THONG)).thenReturn(Optional.of(curriculum(10)));
        var response = service.findMyCurriculum(principal);
        assertThat(response.semesters()).hasSize(10);
        assertThat(response.semesters().getFirst().subjects())
                .extracting(subject -> subject.subjectCode())
                .containsExactly("CS1");
        assertThat(response.semesters().get(1).subjects()).isEmpty();
        assertThat(response.requiredGraduationCredits()).isEqualTo(125);
        assertThat(response.totalCatalogCredits()).isEqualTo(150);
        verify(peopleFacade).findStudentByUserId(7L);
    }

    @Test
    void rejectsUnauthenticatedAndFirstLoginCallsEvenOutsideController() {
        assertThatThrownBy(() -> service.findMyCurriculum(null))
                .isInstanceOfSatisfying(
                        StudentCurriculumException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        var temporary = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("student")
                .claim("token_use", "PASSWORD_CHANGE")
                .build();
        assertThatThrownBy(() -> service.findMyCurriculum(temporary))
                .isInstanceOfSatisfying(
                        StudentCurriculumException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FIRST_LOGIN_REQUIRED));
        verifyNoInteractions(identityFacade, peopleFacade, catalog);
    }

    @Test
    void missingAssignmentIsEmptyButInvalidRoadmapIsAnExplicitFailure() {
        assertThat(service.findCurriculum(null, 3L, TrainingType.CHINH_QUY)).isEmpty();
        verifyNoInteractions(catalog);
        var curriculum = curriculum(10);
        curriculum.getSubjects().getFirst().setRecommendSemester(11);
        when(catalog.findCurriculum(2L, 3L, TrainingType.CHINH_QUY)).thenReturn(Optional.of(curriculum));
        assertThatThrownBy(() -> service.findCurriculum(2L, 3L, TrainingType.CHINH_QUY))
                .isInstanceOfSatisfying(
                        StudentCurriculumException.class, exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CURRICULUM_DATA_INCOMPLETE));
    }

    @Test
    void readsPrerequisitesWithoutActiveSubjectChecksOrPessimisticLocks() {
        var subject = Subject.builder().subjectCode("CS1").build();
        subject.setId(1L);
        var inactivePrerequisite = Subject.builder()
                .subjectCode("PRE1")
                .subjectName("Tiên quyết")
                .status(SubjectStatus.INACTIVE)
                .build();
        inactivePrerequisite.setId(2L);
        var link = SubjectPrerequisite.builder()
                .subject(subject)
                .prerequisiteSubject(inactivePrerequisite)
                .build();
        when(prerequisiteRepository.findAllBySubject_IdInAndDeletedFalse(Set.of(1L)))
                .thenReturn(List.of(link));
        assertThat(service.findPrerequisitesBySubjectIds(Set.of(1L)).getFirst().prerequisites())
                .extracting(prerequisite -> prerequisite.getId())
                .containsExactly(2L);
        verifyNoInteractions(catalog, peopleFacade, identityFacade);
    }

    private CurriculumResponse curriculum(int semesterCount) {
        return CurriculumResponse.builder()
                .id(4L)
                .majorId(2L)
                .cohortId(3L)
                .durationSemesters(semesterCount)
                .requiredGraduationCredits(125)
                .totalCredits(150)
                .subjects(List.of(CurriculumSubjectResponse.builder()
                        .subjectId(1L)
                        .subjectCode("CS1")
                        .credits(3)
                        .subjectType(SubjectType.REQUIRED)
                        .recommendSemester(1)
                        .build()))
                .build();
    }
}
