package com.eduhub.edu_hub.people.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.CreateCohortRequest;
import com.eduhub.edu_hub.people.application.exception.CohortException;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.repository.CohortRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.jwt.Jwt;

class CohortApplicationServiceTest {
    private final CohortRepository cohortRepository = mock(CohortRepository.class);
    private final IdentityFacade identityFacade = mock(IdentityFacade.class);
    private final CohortApplicationService cohortApplicationService =
            new CohortApplicationService(cohortRepository, identityFacade);
    private final Jwt authenticatedJwt = Jwt.withTokenValue("test-token")
            .header("alg", "HS256")
            .subject("academic-user")
            .claim("token_use", "ACCESS")
            .build();

    @BeforeEach
    void setup() {
        when(identityFacade.requireCurrentUser(authenticatedJwt))
                .thenReturn(UserInfoResponse.builder().id(7L).build());
    }

    @Test
    void allocatesTheNextHistoricalSequenceAfterLockingAndRecordsTheActor() {
        when(cohortRepository.findLastIssuedCodeSequence()).thenReturn(8);
        when(cohortRepository.saveAndFlush(any(Cohort.class))).thenAnswer(invocation -> {
            Cohort cohort = invocation.getArgument(0);
            cohort.setId(42L);
            return cohort;
        });

        var response = cohortApplicationService.createCohort(authenticatedJwt, createCohortRequest(2027, 2031));

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.cohortCode()).isEqualTo("K9");
        assertThat(response.startYear()).isEqualTo(2027);
        assertThat(response.endYear()).isEqualTo(2031);
        var cohortCaptor = ArgumentCaptor.forClass(Cohort.class);
        // Chỉ kiểm tra thứ tự điều phối; unit test không chứng minh khóa hoặc tính nguyên tử của database thật.
        var creationOrder = inOrder(identityFacade, cohortRepository);
        creationOrder.verify(identityFacade).requireCurrentUser(authenticatedJwt);
        creationOrder.verify(cohortRepository).lockForCohortCreation();
        creationOrder.verify(cohortRepository).existsByStartYear(2027);
        creationOrder.verify(cohortRepository).findLastIssuedCodeSequence();
        creationOrder.verify(cohortRepository).existsByNormalizedCohortCode("K9");
        creationOrder.verify(cohortRepository).saveAndFlush(cohortCaptor.capture());
        Cohort createdCohort = cohortCaptor.getValue();
        assertThat(createdCohort.getCodeSequence()).isEqualTo(9);
        assertThat(createdCohort.getCreatedBy()).isEqualTo("7");
        assertThat(createdCohort.getUpdatedBy()).isEqualTo("7");
        assertThat(createdCohort.isDeleted()).isFalse();
    }

    @Test
    void startsAtK1WhenNoSequenceHasBeenIssued() {
        when(cohortRepository.saveAndFlush(any(Cohort.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = cohortApplicationService.createCohort(authenticatedJwt, createCohortRequest(2027, 2030));

        assertThat(response.cohortCode()).isEqualTo("K1");
    }

    @Test
    void rejectsAnExistingStartYearBeforeAllocatingACode() {
        when(cohortRepository.existsByStartYear(2027)).thenReturn(true);

        assertThatThrownBy(
                        () -> cohortApplicationService.createCohort(authenticatedJwt, createCohortRequest(2027, 2031)))
                .isInstanceOfSatisfying(CohortException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COHORT_ALREADY_EXISTS);
                    assertThat(exception.getField()).isEqualTo("startYear");
                });
        verify(cohortRepository, never()).findLastIssuedCodeSequence();
        verify(cohortRepository, never()).saveAndFlush(any());
    }

    @Test
    void keepsTheSequenceLimitEvenWhenTheLastIssuedCohortIsHistorical() {
        when(cohortRepository.findLastIssuedCodeSequence()).thenReturn(99);

        assertThatThrownBy(
                        () -> cohortApplicationService.createCohort(authenticatedJwt, createCohortRequest(2027, 2031)))
                .isInstanceOfSatisfying(CohortException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.COHORT_CODE_SEQUENCE_EXCEEDED));
        verify(cohortRepository, never()).existsByNormalizedCohortCode(anyString());
        verify(cohortRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesToOverwriteALegacyCodeWhoseSequenceNeedsReconciliation() {
        when(cohortRepository.findLastIssuedCodeSequence()).thenReturn(8);
        when(cohortRepository.existsByNormalizedCohortCode("K9")).thenReturn(true);

        assertThatThrownBy(
                        () -> cohortApplicationService.createCohort(authenticatedJwt, createCohortRequest(2027, 2031)))
                .isInstanceOfSatisfying(CohortException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COHORT_ALREADY_EXISTS);
                    assertThat(exception.getField()).isEqualTo("cohortCode");
                });
        verify(cohortRepository, never()).saveAndFlush(any());
    }

    @Test
    void validatesTheDateRangeWhenCalledWithoutControllerValidation() {
        assertThatThrownBy(
                        () -> cohortApplicationService.createCohort(authenticatedJwt, createCohortRequest(2027, 2027)))
                .isInstanceOfSatisfying(CohortException.class, exception -> assertThat(exception.getField())
                        .isEqualTo("endYear"));
        assertThatThrownBy(() -> cohortApplicationService.createCohort(authenticatedJwt, null))
                .isInstanceOf(CohortException.class);
        verifyNoInteractions(cohortRepository);
    }

    @Test
    void doesNotCreateACohortForAnInactiveAccountOrInvalidSession() {
        var authenticationException = new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED);
        when(identityFacade.requireCurrentUser(authenticatedJwt)).thenThrow(authenticationException);

        assertThatThrownBy(
                        () -> cohortApplicationService.createCohort(authenticatedJwt, createCohortRequest(2027, 2031)))
                .isSameAs(authenticationException);
        verifyNoInteractions(cohortRepository);
    }

    @Test
    void propagatesAFailedLockWithoutAttemptingToAllocateOrSave() {
        var databaseException = new DataAccessResourceFailureException("Không thể khóa bảng khóa tuyển sinh");
        doThrow(databaseException).when(cohortRepository).lockForCohortCreation();

        assertThatThrownBy(
                        () -> cohortApplicationService.createCohort(authenticatedJwt, createCohortRequest(2027, 2031)))
                .isSameAs(databaseException);
        verify(cohortRepository, never()).findLastIssuedCodeSequence();
        verify(cohortRepository, never()).saveAndFlush(any());
    }

    @Test
    void propagatesSaveFailuresWithoutReturningASuccessResponse() {
        var databaseException = new DataAccessResourceFailureException("Không thể lưu khóa tuyển sinh");
        when(cohortRepository.saveAndFlush(any(Cohort.class))).thenThrow(databaseException);

        assertThatThrownBy(
                        () -> cohortApplicationService.createCohort(authenticatedJwt, createCohortRequest(2027, 2031)))
                .isSameAs(databaseException);
    }

    private CreateCohortRequest createCohortRequest(int startYear, int endYear) {
        var request = new CreateCohortRequest();
        request.setStartYear(startYear);
        request.setEndYear(endYear);
        return request;
    }
}
