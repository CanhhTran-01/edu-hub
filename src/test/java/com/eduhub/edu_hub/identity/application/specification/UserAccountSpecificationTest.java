package com.eduhub.edu_hub.identity.application.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.domain.enums.RoleName;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.Role;
import com.eduhub.edu_hub.identity.domain.model.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserAccountSpecificationTest {
    private final Root<User> accountRoot = mock(Root.class);
    private final CriteriaQuery<User> accountQuery = mock(CriteriaQuery.class);
    private final CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);

    @Test
    void keywordMatchesUsernameOrEmailAndEscapesLiteralWildcards() {
        Path<String> username = mock(Path.class);
        Path<String> email = mock(Path.class);
        Expression<String> lowercaseUsername = mock(Expression.class);
        Expression<String> lowercaseEmail = mock(Expression.class);
        Predicate usernameMatch = mock(Predicate.class);
        Predicate emailMatch = mock(Predicate.class);
        Predicate eitherFieldMatches = mock(Predicate.class);
        when(accountRoot.<String>get("username")).thenReturn(username);
        when(accountRoot.<String>get("email")).thenReturn(email);
        when(criteriaBuilder.lower(username)).thenReturn(lowercaseUsername);
        when(criteriaBuilder.lower(email)).thenReturn(lowercaseEmail);
        String expectedPattern = "%a\\_b\\%\\\\c%";
        when(criteriaBuilder.like(lowercaseUsername, expectedPattern, '\\')).thenReturn(usernameMatch);
        when(criteriaBuilder.like(lowercaseEmail, expectedPattern, '\\')).thenReturn(emailMatch);
        when(criteriaBuilder.or(usernameMatch, emailMatch)).thenReturn(eitherFieldMatches);

        var predicate = UserAccountSpecification.matchesKeyword(" A_B%\\C ")
                .toPredicate(accountRoot, accountQuery, criteriaBuilder);

        assertThat(predicate).isSameAs(eitherFieldMatches);
        verify(criteriaBuilder).or(usernameMatch, emailMatch);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void absentKeywordDoesNotRestrictTheInitialAccountList(String keyword) {
        assertThat(UserAccountSpecification.matchesKeyword(keyword)
                        .toPredicate(accountRoot, accountQuery, criteriaBuilder))
                .isNull();
        verifyNoInteractions(accountRoot, criteriaBuilder);
    }

    @Test
    void roleStatusAndPasswordChangeFiltersUseTheirExactAccountFields() {
        Path<Role> role = mock(Path.class);
        Path<String> roleName = mock(Path.class);
        Path<UserStatus> status = mock(Path.class);
        Path<Boolean> firstLogin = mock(Path.class);
        when(accountRoot.<Role>get("role")).thenReturn(role);
        when(role.<String>get("roleName")).thenReturn(roleName);
        when(accountRoot.<UserStatus>get("status")).thenReturn(status);
        when(accountRoot.<Boolean>get("firstLogin")).thenReturn(firstLogin);

        UserAccountSpecification.hasRole(RoleName.LECTURER).toPredicate(accountRoot, accountQuery, criteriaBuilder);
        UserAccountSpecification.hasStatus(UserStatus.DISABLED).toPredicate(accountRoot, accountQuery, criteriaBuilder);
        UserAccountSpecification.requiresPasswordChange(false).toPredicate(accountRoot, accountQuery, criteriaBuilder);
        UserAccountSpecification.requiresPasswordChange(true).toPredicate(accountRoot, accountQuery, criteriaBuilder);

        verify(criteriaBuilder).equal(roleName, "LECTURER");
        verify(criteriaBuilder).equal(status, UserStatus.DISABLED);
        verify(criteriaBuilder).equal(firstLogin, false);
        verify(criteriaBuilder).equal(firstLogin, true);
    }

    @Test
    void omittedFiltersDoNotBecomeFalseOrDefaultStatusConditions() {
        assertThat(UserAccountSpecification.hasRole(null).toPredicate(accountRoot, accountQuery, criteriaBuilder))
                .isNull();
        assertThat(UserAccountSpecification.hasStatus(null).toPredicate(accountRoot, accountQuery, criteriaBuilder))
                .isNull();
        assertThat(UserAccountSpecification.requiresPasswordChange(null)
                        .toPredicate(accountRoot, accountQuery, criteriaBuilder))
                .isNull();
        verifyNoInteractions(accountRoot, criteriaBuilder);
    }

    @Test
    void softDeletedAccountsAreExcluded() {
        Path<Boolean> deleted = mock(Path.class);
        Predicate notDeleted = mock(Predicate.class);
        when(accountRoot.<Boolean>get("deleted")).thenReturn(deleted);
        when(criteriaBuilder.isFalse(deleted)).thenReturn(notDeleted);

        assertThat(UserAccountSpecification.notDeleted().toPredicate(accountRoot, accountQuery, criteriaBuilder))
                .isSameAs(notDeleted);
    }
}
