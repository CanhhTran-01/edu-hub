package com.eduhub.edu_hub.identity.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.shared.config.TimeConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RefreshTokenCleanupServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T19:00:00Z"), TimeConfig.APPLICATION_ZONE);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final RefreshTokenCleanupService service =
            new RefreshTokenCleanupService(refreshTokenRepository, transactionManager, CLOCK);

    @BeforeEach
    void configureTransactions() {
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
    }

    @Test
    void deletesBatchesWithAFixedVietnamCutoffAndCommitsEachBatchSeparately() {
        when(refreshTokenRepository.deleteExpiredBatch(any(), anyInt())).thenReturn(1000, 3);
        assertThat(service.deleteExpiredRefreshTokens()).isEqualTo(1003);
        verify(refreshTokenRepository, times(2)).deleteExpiredBatch(LocalDateTime.of(2026, 11, 25, 2, 0), 1000);
        verify(transactionManager, times(2)).commit(any());
        var transactionDefinitions = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager, times(2)).getTransaction(transactionDefinitions.capture());
        assertThat(transactionDefinitions.getAllValues())
                .allSatisfy(definition -> assertThat(definition.getPropagationBehavior())
                        .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    @Test
    void stopsAfterAnEmptyBatch() {
        assertThat(service.deleteExpiredRefreshTokens()).isZero();
        verify(refreshTokenRepository).deleteExpiredBatch(any(), anyInt());
        verify(transactionManager).commit(any());
    }

    @Test
    void stopsAndRollsBackOnlyTheFailingBatch() {
        when(refreshTokenRepository.deleteExpiredBatch(any(), anyInt()))
                .thenReturn(1000)
                .thenThrow(new IllegalStateException("failed"));
        assertThatThrownBy(service::deleteExpiredRefreshTokens).isInstanceOf(IllegalStateException.class);
        verify(transactionManager).commit(any());
        verify(transactionManager).rollback(any());
        verify(refreshTokenRepository, times(2)).deleteExpiredBatch(any(), anyInt());
    }
}
