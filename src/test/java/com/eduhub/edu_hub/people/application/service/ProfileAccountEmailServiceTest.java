package com.eduhub.edu_hub.people.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.eduhub.edu_hub.identity.api.dto.response.UserAccountEmailResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProfileAccountEmailServiceTest {
    private final IdentityFacade identityFacade = mock(IdentityFacade.class);
    private final ProfileAccountEmailService profileAccountEmailService =
            new ProfileAccountEmailService(identityFacade);

    @Test
    void emptyUserIdsDoNotCallIdentity() {
        assertThat(profileAccountEmailService.findEmailsByUserIds(List.of())).isEmpty();
        verifyNoInteractions(identityFacade);
    }

    @Test
    void profilesWithoutLinkedUsersDoNotCallIdentity() {
        assertThat(profileAccountEmailService.findEmailsByUserIds(Collections.singletonList(null)))
                .isEmpty();
        verifyNoInteractions(identityFacade);
    }

    @Test
    void nullAndDuplicateUserIdsAreRemovedBeforeOneFacadeCall() {
        when(identityFacade.findAccountEmailsByUserIds(Set.of(101L, 102L)))
                .thenReturn(List.of(
                        new UserAccountEmailResponse(102L, "second@example.com"),
                        new UserAccountEmailResponse(101L, "first@example.com")));

        var accountEmailsByUserId =
                profileAccountEmailService.findEmailsByUserIds(Arrays.asList(101L, null, 101L, 102L));

        assertThat(accountEmailsByUserId)
                .hasSize(2)
                .containsEntry(101L, "first@example.com")
                .containsEntry(102L, "second@example.com");
        verify(identityFacade).findAccountEmailsByUserIds(Set.of(101L, 102L));
        verifyNoMoreInteractions(identityFacade);
    }

    @Test
    void accountsSharingAnEmailRemainSeparateByUserId() {
        when(identityFacade.findAccountEmailsByUserIds(Set.of(101L, 102L)))
                .thenReturn(List.of(
                        new UserAccountEmailResponse(101L, "shared@example.com"),
                        new UserAccountEmailResponse(102L, "shared@example.com")));

        assertThat(profileAccountEmailService.findEmailsByUserIds(List.of(101L, 102L)))
                .hasSize(2)
                .containsEntry(101L, "shared@example.com")
                .containsEntry(102L, "shared@example.com");
    }

    @Test
    void nullEmailsAndMissingAccountsHaveNoMapEntry() {
        when(identityFacade.findAccountEmailsByUserIds(Set.of(101L, 102L, 103L)))
                .thenReturn(List.of(
                        new UserAccountEmailResponse(101L, "current@example.com"),
                        new UserAccountEmailResponse(102L, null)));

        var accountEmailsByUserId = profileAccountEmailService.findEmailsByUserIds(List.of(101L, 102L, 103L));

        assertThat(accountEmailsByUserId)
                .hasSize(1)
                .containsEntry(101L, "current@example.com")
                .doesNotContainKeys(102L, 103L);
        assertThat(accountEmailsByUserId.get(102L)).isNull();
        assertThat(accountEmailsByUserId.get(103L)).isNull();
    }

    @Test
    void identityFailureIsPropagatedToTheCaller() {
        var identityFailure = new IllegalStateException("Nguồn email tài khoản không khả dụng");
        when(identityFacade.findAccountEmailsByUserIds(Set.of(101L))).thenThrow(identityFailure);

        assertThatThrownBy(() -> profileAccountEmailService.findEmailsByUserIds(List.of(101L)))
                .isSameAs(identityFailure);
    }
}
