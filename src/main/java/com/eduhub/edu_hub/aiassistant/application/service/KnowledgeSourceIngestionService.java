package com.eduhub.edu_hub.aiassistant.application.service;

import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort;
import com.eduhub.edu_hub.aiassistant.domain.enums.KnowledgeSourceStatus;
import com.eduhub.edu_hub.aiassistant.domain.repository.KnowledgeSourceRepository;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Worker gọi các transaction ngắn này; trích văn bản và gọi embedding nằm ngoài transaction. */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeSourceIngestionService {
    private final KnowledgeSourceRepository sourceRepository;
    private final KnowledgeChunkPort chunkPort;
    private final AiRagProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<Long> findPendingSources() {
        return sourceRepository.findPendingIds(KnowledgeSourceStatus.PROCESSING, PageRequest.of(0, 10));
    }

    @Transactional
    public IngestionTask claimSource(Long sourceId) {
        var source = sourceRepository.findForUpdate(sourceId).orElse(null);
        if (source == null
                || source.isDeleted()
                || source.getStatus() != KnowledgeSourceStatus.PROCESSING
                || source.getProcessingToken() != null) return null;
        String processingToken = UUID.randomUUID().toString();
        source.setProcessingToken(processingToken);
        source.setProcessingStartedAt(LocalDateTime.now(clock));
        chunkPort.deleteUnpublishedChunks(sourceId);
        return new IngestionTask(
                sourceId, processingToken, source.getOriginalFilename(), sourceRepository.findOriginalFile(sourceId));
    }

    @Transactional
    public void completeSource(IngestionTask task, int expectedChunks) {
        var source = sourceRepository.findForUpdate(task.sourceId()).orElse(null);
        if (source == null
                || source.isDeleted()
                || source.getStatus() != KnowledgeSourceStatus.PROCESSING
                || !Objects.equals(source.getProcessingToken(), task.processingToken())) return;
        if (expectedChunks < 1
                || chunkPort.countChunks(source.getId()) != expectedChunks
                || !chunkPort.hasCompleteIndex(source.getId())) {
            throw new IllegalStateException("Chỉ mục chưa lưu đủ đoạn tri thức");
        }
        source.setStatus(KnowledgeSourceStatus.ACTIVE);
        source.setProcessingToken(null);
        source.setProcessingStartedAt(null);
        source.setErrorMessage(null);
        sourceRepository.flush();
        // Bản mới có ngày hiệu lực tương lai vẫn giữ bản cũ; bộ lọc truy xuất và lần quét đến hạn xử lý cùng quy tắc.
        sourceRepository.deactivateSupersededSources(
                LocalDate.now(clock), LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        log.info("Đã xử lý nguồn tri thức: sourceId={}, chunkCount={}", source.getId(), expectedChunks);
    }

    @Transactional
    public void failSource(IngestionTask task, String failureReason) {
        var source = sourceRepository.findForUpdate(task.sourceId()).orElse(null);
        if (source == null
                || source.isDeleted()
                || source.getStatus() != KnowledgeSourceStatus.PROCESSING
                || !Objects.equals(source.getProcessingToken(), task.processingToken())) return;
        chunkPort.deleteUnpublishedChunks(source.getId());
        source.setStatus(KnowledgeSourceStatus.FAILED);
        source.setProcessingToken(null);
        source.setProcessingStartedAt(null);
        source.setErrorMessage(failureReason);
        log.warn("Xử lý nguồn tri thức thất bại: sourceId={}, reason={}", source.getId(), failureReason);
    }

    @Transactional
    public void recoverInterruptedSources() {
        var interruptedBefore =
                LocalDateTime.now(clock).minus(properties.getIngestionTimeout()).minusSeconds(30);
        for (Long sourceId : sourceRepository.findInterruptedIds(
                KnowledgeSourceStatus.PROCESSING, interruptedBefore, PageRequest.of(0, 100))) {
            var source = sourceRepository.findForUpdate(sourceId).orElseThrow();
            if (source.getStatus() != KnowledgeSourceStatus.PROCESSING
                    || source.getProcessingStartedAt() == null
                    || !source.getProcessingStartedAt().isBefore(interruptedBefore)) continue;
            chunkPort.deleteUnpublishedChunks(sourceId);
            source.setStatus(KnowledgeSourceStatus.FAILED);
            source.setProcessingToken(null);
            source.setProcessingStartedAt(null);
            source.setErrorMessage("Tác vụ bị gián đoạn hoặc quá thời hạn. Vui lòng yêu cầu xử lý lại.");
            log.warn("Đánh dấu tác vụ nguồn tri thức bị gián đoạn: sourceId={}", sourceId);
        }
        sourceRepository.deactivateSupersededSources(
                LocalDate.now(clock), LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    public record IngestionTask(Long sourceId, String processingToken, String filename, byte[] originalFile) {}
}
