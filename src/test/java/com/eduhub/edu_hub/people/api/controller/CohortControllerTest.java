package com.eduhub.edu_hub.people.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.exception.CohortExceptionHandler;
import com.eduhub.edu_hub.people.application.service.CohortApplicationService;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.repository.CohortRepository;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.config.TimeConfig;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CohortControllerTest {
    private final CohortRepository cohortRepository = mock(CohortRepository.class);
    private final IdentityFacade identityFacade = mock(IdentityFacade.class);
    private final ApiResponseFactory apiResponseFactory =
            new ApiResponseFactory(Clock.fixed(Instant.parse("2026-11-24T00:00:00Z"), TimeConfig.APPLICATION_ZONE));
    private final Jwt authenticatedJwt = createPrincipal("ACCESS");
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(authenticatedJwt));
        when(identityFacade.requireCurrentUser(authenticatedJwt))
                .thenReturn(
                        UserInfoResponse.builder().id(7L).role("ACADEMIC_ADMIN").build());
        mvc = MockMvcBuilders.standaloneSetup(new CohortController(
                        apiResponseFactory, new CohortApplicationService(cohortRepository, identityFacade)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(
                        new CohortExceptionHandler(apiResponseFactory), new GlobalExceptionHandler(apiResponseFactory))
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsHistoricalCohortsAndNullableYearsWithoutExposingEntities() throws Exception {
        var currentCohort = new Cohort();
        currentCohort.setId(9L);
        currentCohort.setCohortCode("K09");
        currentCohort.setStartYear(2026);
        currentCohort.setEndYear(2030);
        var historicalCohort = new Cohort();
        historicalCohort.setId(1L);
        historicalCohort.setCohortCode("K01");
        when(cohortRepository.findAllByDeletedFalseOrderByIdDesc())
                .thenReturn(List.of(currentCohort, historicalCohort));

        mvc.perform(get("/api/v1/cohorts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(9))
                .andExpect(jsonPath("$.data[0].cohortCode").value("K09"))
                .andExpect(jsonPath("$.data[0].startYear").value(2026))
                .andExpect(jsonPath("$.data[0].endYear").value(2030))
                .andExpect(jsonPath("$.data[1].cohortCode").value("K01"))
                .andExpect(jsonPath("$.data[1].startYear").value(nullValue()))
                .andExpect(jsonPath("$.data[1].endYear").value(nullValue()))
                .andExpect(jsonPath("$.data[0].codeSequence").doesNotExist())
                .andExpect(jsonPath("$.data[0].deleted").doesNotExist());
        verify(cohortRepository).findAllByDeletedFalseOrderByIdDesc();
        verifyNoMoreInteractions(cohortRepository);
    }

    @Test
    void returnsAnEmptyListWhenNoUndeletedCohortsExist() throws Exception {
        when(cohortRepository.findAllByDeletedFalseOrderByIdDesc()).thenReturn(List.of());

        mvc.perform(get("/api/v1/cohorts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void createsACohortThatCanBeSelectedFromTheExistingAdmissionDropdown() throws Exception {
        when(cohortRepository.findLastIssuedCodeSequence()).thenReturn(8);
        when(cohortRepository.saveAndFlush(any(Cohort.class))).thenAnswer(invocation -> {
            Cohort cohort = invocation.getArgument(0);
            cohort.setId(42L);
            return cohort;
        });

        mvc.perform(post("/api/v1/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startYear\":2027,\"endYear\":2031}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.cohortCode").value("K9"))
                .andExpect(jsonPath("$.data.startYear").value(2027))
                .andExpect(jsonPath("$.data.endYear").value(2031))
                .andExpect(jsonPath("$.data.codeSequence").doesNotExist())
                .andExpect(jsonPath("$.data.createdBy").doesNotExist())
                .andExpect(jsonPath("$.data.deleted").doesNotExist());

        var cohortCaptor = ArgumentCaptor.forClass(Cohort.class);
        verify(cohortRepository).saveAndFlush(cohortCaptor.capture());
        assertThat(cohortCaptor.getValue().getCodeSequence()).isEqualTo(9);
        when(cohortRepository.findAllByDeletedFalseOrderByIdDesc()).thenReturn(List.of(cohortCaptor.getValue()));
        mvc.perform(get("/api/v1/cohorts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(42))
                .andExpect(jsonPath("$.data[0].cohortCode").value("K9"));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "{}|startYear",
                "{\"startYear\":2027}|endYear",
                "{\"startYear\":null,\"endYear\":2031}|startYear",
                "{\"startYear\":2027,\"endYear\":null}|endYear",
                "{\"startYear\":0,\"endYear\":2031}|startYear",
                "{\"startYear\":9999,\"endYear\":2031}|startYear",
                "{\"startYear\":2027,\"endYear\":10000}|endYear",
                "{\"startYear\":2027,\"endYear\":2027}|endYear",
                "{\"startYear\":2027,\"endYear\":2026}|endYear"
            })
    void rejectsMissingOrInvalidYearsWithFieldErrors(String requestBody, String expectedField) throws Exception {
        mvc.perform(post("/api/v1/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.hasItem(expectedField)));
        verifyNoInteractions(cohortRepository);
    }

    @ParameterizedTest
    @MethodSource("invalidYearTokens")
    void rejectsYearCoercionAndOverflow(String field, String jsonToken) throws Exception {
        String requestBody = "{\"startYear\":%s,\"endYear\":%s}"
                .formatted(
                        "startYear".equals(field) ? jsonToken : "2027", "endYear".equals(field) ? jsonToken : "2031");
        mvc.perform(post("/api/v1/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.errors[0].field").value(field));
        verifyNoInteractions(cohortRepository, identityFacade);
    }

    private static Stream<Arguments> invalidYearTokens() {
        return Stream.of("startYear", "endYear")
                .flatMap(field -> Stream.of("\"2027\"", "2027.0", "true", "[]", "{}", "2147483648")
                        .map(jsonToken -> Arguments.of(field, jsonToken)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cohortCode", "codeSequence", "id", "majorId", "deleted"})
    void rejectsClientSuppliedFieldsEvenWhenTheyAreNull(String unsupportedField) throws Exception {
        mvc.perform(post("/api/v1/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startYear\":2027,\"endYear\":2031,\"%s\":null}".formatted(unsupportedField)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.errors[0].field").value(unsupportedField));
        verifyNoInteractions(cohortRepository, identityFacade);
    }

    @Test
    void returnsAConflictOnTheStartYearAlreadyInUse() throws Exception {
        when(cohortRepository.existsByStartYear(2027)).thenReturn(true);
        mvc.perform(post("/api/v1/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startYear\":2027,\"endYear\":2031}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4124))
                .andExpect(jsonPath("$.errors[0].field").value("startYear"));
        verify(cohortRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @CsvSource({
        "uk_cohorts_start_year,startYear",
        "uk_cohorts_code_normalized,cohortCode",
        "uk_cohorts_code_sequence,cohortCode"
    })
    void mapsKnownDatabaseUniquenessConflictsToTheRelevantField(String constraintName, String expectedField)
            throws Exception {
        var databaseException = new ConstraintViolationException(
                "Chi tiết SQL nội bộ", new SQLException("Chi tiết SQL nội bộ"), constraintName);
        when(cohortRepository.saveAndFlush(any(Cohort.class)))
                .thenThrow(new DataIntegrityViolationException("Chi tiết SQL nội bộ", databaseException));
        mvc.perform(post("/api/v1/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startYear\":2027,\"endYear\":2031}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4124))
                .andExpect(jsonPath("$.errors[0].field").value(expectedField))
                .andExpect(content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL nội bộ"))));
    }

    @Test
    void doesNotConvertAnUnrelatedDatabaseFailureIntoADuplicateCohort() throws Exception {
        when(cohortRepository.saveAndFlush(any(Cohort.class)))
                .thenThrow(new DataIntegrityViolationException("Chi tiết SQL nội bộ"));
        mvc.perform(post("/api/v1/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startYear\":2027,\"endYear\":2031}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL nội bộ"))));
    }

    @Test
    void rejectsMissingPrincipalBeforeAccessingBusinessData() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(post("/api/v1/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startYear\":2027,\"endYear\":2031}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(4000));
        verifyNoInteractions(cohortRepository, identityFacade);
    }

    @Test
    void rejectsFirstLoginTokens() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(createPrincipal("PASSWORD_CHANGE")));
        mvc.perform(post("/api/v1/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startYear\":2027,\"endYear\":2031}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4006));
        verifyNoInteractions(cohortRepository, identityFacade);
    }

    private static Jwt createPrincipal(String tokenPurpose) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("academic-user")
                .claim("uid", 7L)
                .claim("sid", 3L)
                .claim("token_use", tokenPurpose)
                .build();
    }
}
