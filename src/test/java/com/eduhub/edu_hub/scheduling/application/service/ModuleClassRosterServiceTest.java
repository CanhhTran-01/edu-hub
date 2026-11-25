package com.eduhub.edu_hub.scheduling.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.enrollment.application.facade.EnrollmentFacade;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.scheduling.api.dto.request.ModuleClassRosterSearchRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassResponse;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassRosterException;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.ModuleClass;
import com.eduhub.edu_hub.scheduling.domain.model.Semester;
import com.eduhub.edu_hub.scheduling.domain.repository.ModuleClassRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.SemesterRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.persistence.criteria.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;

class ModuleClassRosterServiceTest {
    private final IdentityFacade identity = mock(IdentityFacade.class);
    private final PeopleFacade.Scheduling people = mock(PeopleFacade.Scheduling.class);
    private final CurriculumFacade.ClassOpening curriculum = mock(CurriculumFacade.ClassOpening.class);
    private final EnrollmentFacade.ClassRoster enrollment = mock(EnrollmentFacade.ClassRoster.class);
    private final SemesterRepository semesters = mock(SemesterRepository.class);
    private final ModuleClassRepository classes = mock(ModuleClassRepository.class);
    private final ModuleClassApplicationService classService = mock(ModuleClassApplicationService.class);
    private final ModuleClassRosterService service =
            new ModuleClassRosterService(identity, people, curriculum, enrollment, semesters, classes, classService);
    private final Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("giang-vien")
            .claim("uid", 9L)
            .claim("sid", 8L)
            .claim("role", "ACADEMIC_ADMIN")
            .claim("token_use", "ACCESS")
            .build();

    @Test
    void rejectsMissingPrincipalAndPasswordChangeTokensWithoutReadingData() {
        expectError(() -> service.findRoster(null, 100L), ErrorCode.UNAUTHENTICATED);
        var restricted = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("token_use", "PASSWORD_CHANGE")
                .build();
        expectError(() -> service.findRoster(restricted, 100L), ErrorCode.FIRST_LOGIN_REQUIRED);
        verifyNoInteractions(identity, classes, semesters, people, enrollment, classService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "SYSTEM_ADMIN", "", "OTHER"})
    void deniesRolesOutsideThisUseCase(String role) {
        authenticateAs(role);
        expectError(() -> service.findRoster(principal, 100L), ErrorCode.UNAUTHORIZED);
        expectError(
                () -> service.searchClasses(principal, 7L, new ModuleClassRosterSearchRequest()),
                ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(classes, semesters, people, enrollment, classService);
    }

    @Test
    void propagatesInvalidAccountOrRevokedSessionWithoutReadingClassData() {
        when(identity.requireCurrentUser(principal))
                .thenThrow(new com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException(
                        ErrorCode.UNAUTHENTICATED));
        assertThatThrownBy(() -> service.findRoster(principal, 100L))
                .isInstanceOf(com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException.class);
        verifyNoInteractions(classes, semesters, people, enrollment, classService);
    }

    @Test
    void lecturerWithoutLinkedProfileNeverFallsBackToAllClasses() {
        authenticateAs("LECTURER");
        when(people.findLecturerIdByUserId(9L)).thenReturn(Optional.empty());
        expectError(
                () -> service.searchClasses(principal, 7L, new ModuleClassRosterSearchRequest()),
                ErrorCode.UNAUTHORIZED);
        expectError(() -> service.findRoster(principal, 100L), ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(classes, semesters, enrollment, classService);
    }

    @Test
    void usesDatabaseRoleAndLinkedLecturerIdToBlockAnotherLecturersClass() {
        authenticateAs("LECTURER");
        when(people.findLecturerIdByUserId(9L)).thenReturn(Optional.of(201L));
        when(classes.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(moduleClass(202L, ModuleClassStatus.OPEN)));
        expectError(() -> service.findRoster(principal, 100L), ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(enrollment, classService, curriculum);
    }

    @Test
    void assignedLecturerCanReadCompletedClassInLockedPastSemester() {
        authenticateAs("LECTURER");
        when(people.findLecturerIdByUserId(9L)).thenReturn(Optional.of(201L));
        var moduleClass = moduleClass(201L, ModuleClassStatus.COMPLETED);
        moduleClass.getSemester().setLocked(true);
        when(classes.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(moduleClass));
        var details = ModuleClassResponse.builder().id(100L).semesterId(7L).build();
        when(classService.findModuleClassById(100L)).thenReturn(details);
        when(enrollment.findStudentsByModuleClassId(100L)).thenReturn(List.of());
        assertThat(service.findRoster(principal, 100L).moduleClass()).isSameAs(details);
    }

    @Test
    void academicAdminCanReadAnUnassignedEmptyClass() {
        authenticateAs("ACADEMIC_ADMIN");
        when(classes.findByIdAndDeletedFalse(100L))
                .thenReturn(Optional.of(moduleClass(null, ModuleClassStatus.UNSCHEDULED)));
        when(classService.findModuleClassById(100L))
                .thenReturn(ModuleClassResponse.builder().id(100L).build());
        assertThat(service.findRoster(principal, 100L).students()).isEmpty();
        verify(people, never()).findLecturerIdByUserId(anyLong());
    }

    @Test
    void rejectsMissingCancelledOrDeletedSemesterBeforeLoadingRoster() {
        authenticateAs("ACADEMIC_ADMIN");
        when(classes.findByIdAndDeletedFalse(100L)).thenReturn(Optional.empty());
        expectError(() -> service.findRoster(principal, 100L), ErrorCode.MODULE_CLASS_NOT_FOUND);
        var moduleClass = moduleClass(null, ModuleClassStatus.CANCELLED);
        when(classes.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(moduleClass));
        expectError(() -> service.findRoster(principal, 100L), ErrorCode.MODULE_CLASS_CANCELLED);
        moduleClass.getSemester().setDeleted(true);
        expectError(() -> service.findRoster(principal, 100L), ErrorCode.SEMESTER_NOT_FOUND);
        verifyNoInteractions(enrollment, classService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void lecturerSearchAppliesScopeInTheQueryAndPreservesTotalOnAnEmptyPage() {
        authenticateAs("LECTURER");
        when(people.findLecturerIdByUserId(9L)).thenReturn(Optional.of(201L));
        when(semesters.findByIdAndDeletedFalse(7L))
                .thenReturn(Optional.of(Semester.builder().build()));
        var request = new ModuleClassRosterSearchRequest();
        request.setKeyword(" CS_% ");
        request.setPage(2);
        when(curriculum.findSubjectIdsByKeyword(" CS_% ")).thenReturn(Set.of(11L, 12L));
        when(classes.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 20), 21));

        var page = service.searchClasses(principal, 7L, request);

        assertThat(page.getTotalElements()).isEqualTo(21);
        assertThat(page.getContent()).isEmpty();
        ArgumentCaptor<Specification<ModuleClass>> criteria = ArgumentCaptor.forClass(Specification.class);
        verify(classes).findAll(criteria.capture(), any(Pageable.class));
        Root<ModuleClass> root = mock(Root.class, RETURNS_DEEP_STUBS);
        var query = mock(CriteriaQuery.class);
        var builder = mock(CriteriaBuilder.class);
        criteria.getValue().toPredicate(root, query, builder);
        verify(builder).equal(root.get("semester").get("id"), 7L);
        verify(builder).equal(root.get("lecturerId"), 201L);
        verify(builder).isFalse(root.get("deleted"));
        verify(builder).isFalse(root.get("semester").get("deleted"));
        verify(builder).notEqual(root.get("status"), ModuleClassStatus.CANCELLED);
        verify(builder).like(any(), eq("%cs\\_\\%%"), eq('\\'));
        verify(root.get("subjectId")).in(Set.of(11L, 12L));
        verify(root, never()).get("subject");
        verify(root, never()).get("lecturer");
        verify(curriculum).findSubjectIdsByKeyword(" CS_% ");
        verifyNoMoreInteractions(curriculum);
        verifyNoInteractions(enrollment, classService);
    }

    @Test
    void rejectsMissingSemesterAndInvalidPagination() {
        authenticateAs("ACADEMIC_ADMIN");
        var request = new ModuleClassRosterSearchRequest();
        expectError(() -> service.searchClasses(principal, 0L, request), ErrorCode.VALIDATION_ERROR);
        request.setPage(-1);
        expectError(() -> service.searchClasses(principal, 7L, request), ErrorCode.VALIDATION_ERROR);
        request.setPage(0);
        expectError(() -> service.searchClasses(principal, 7L, request), ErrorCode.SEMESTER_NOT_FOUND);
        verifyNoInteractions(classes);
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminSearchIncludesUnassignedClassesAndMapsExistingSummaryWithoutLoadingRosters() {
        authenticateAs("ACADEMIC_ADMIN");
        var moduleClass = moduleClass(null, ModuleClassStatus.UNSCHEDULED);
        when(semesters.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(moduleClass.getSemester()));
        when(classes.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(moduleClass), PageRequest.of(0, 20), 1));
        when(curriculum.findSubjectsByIds(java.util.Set.of(11L)))
                .thenReturn(List.of(com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse.builder()
                        .id(11L)
                        .subjectCode("CS101")
                        .subjectName("Lập trình")
                        .credits(3)
                        .build()));

        var page = service.searchClasses(principal, 7L, new ModuleClassRosterSearchRequest());

        assertThat(page.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.getId()).isEqualTo(100L);
            assertThat(summary.getSubjectName()).isEqualTo("Lập trình");
            assertThat(summary.getLecturerName()).isNull();
        });
        ArgumentCaptor<Specification<ModuleClass>> criteria = ArgumentCaptor.forClass(Specification.class);
        verify(classes).findAll(criteria.capture(), any(Pageable.class));
        Root<ModuleClass> root = mock(Root.class, RETURNS_DEEP_STUBS);
        var builder = mock(CriteriaBuilder.class);
        criteria.getValue().toPredicate(root, mock(CriteriaQuery.class), builder);
        var lecturerIdPath = root.get("lecturerId");
        verify(builder, never()).equal(eq(lecturerIdPath), any(Object.class));
        verifyNoInteractions(people, enrollment, classService);
    }

    private void authenticateAs(String role) {
        when(identity.requireCurrentUser(principal))
                .thenReturn(UserInfoResponse.builder().id(9L).role(role).build());
    }

    private ModuleClass moduleClass(Long lecturerId, ModuleClassStatus status) {
        var semester = Semester.builder().semesterCode("HK_1_2025_2026").build();
        semester.setId(7L);
        var lecturer = lecturerId == null ? null : Lecturer.builder().build();
        if (lecturer != null) lecturer.setId(lecturerId);
        var moduleClass = ModuleClass.builder()
                .semester(semester)
                .lecturerId(lecturer == null ? null : lecturer.getId())
                .subjectId(11L)
                .status(status)
                .build();
        moduleClass.setId(100L);
        return moduleClass;
    }

    private void expectError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode expectedCode) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ModuleClassRosterException.class, exception -> assertThat(
                        exception.getErrorCode())
                .isEqualTo(expectedCode));
    }
}
