package com.eduhub.edu_hub.identity.application.service;

import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Dọn token hết hạn theo từng transaction ngắn; token đã revoke còn hạn vẫn cần để phát hiện dùng lại. */
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {
    private static final int CLEANUP_BATCH_SIZE = 1000;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    public long deleteExpiredRefreshTokens() {
        LocalDateTime expiredAt = LocalDateTime.now(clock);
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        long deletedTokenCount = 0;
        int deletedBatchSize;
        do {
            // Chốt một mốc cho cả lượt dọn, không xóa token còn hạn kể cả khi revoked=true.
            deletedBatchSize = Objects.requireNonNull(transaction.execute(
                    status -> refreshTokenRepository.deleteExpiredBatch(expiredAt, CLEANUP_BATCH_SIZE)));
            deletedTokenCount += deletedBatchSize;
        } while (deletedBatchSize == CLEANUP_BATCH_SIZE);
        return deletedTokenCount;
    }
}
