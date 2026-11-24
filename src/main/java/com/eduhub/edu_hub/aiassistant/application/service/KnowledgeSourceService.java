package com.eduhub.edu_hub.aiassistant.application.service;

import com.eduhub.edu_hub.aiassistant.api.dto.request.*;
import com.eduhub.edu_hub.aiassistant.api.dto.response.KnowledgeSourceResponse;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort;
import com.eduhub.edu_hub.aiassistant.application.specification.KnowledgeSourceSpecification;
import com.eduhub.edu_hub.aiassistant.domain.enums.KnowledgeSourceStatus;
import com.eduhub.edu_hub.aiassistant.domain.model.KnowledgeSource;
import com.eduhub.edu_hub.aiassistant.domain.repository.KnowledgeSourceRepository;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.io.IOException;
import java.security.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quản trị nguồn; trạng thái PROCESSING lưu trong DB đồng thời là công việc chờ worker nhận sau commit. */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeSourceService {
    private final AiAccessService accessService;
    private final KnowledgeSourceRepository sourceRepository;
    private final KnowledgeChunkPort chunkPort;
    private final AiRagProperties properties;
    private final Clock clock;

    @Transactional
    public KnowledgeSourceResponse upload(Jwt principal, UploadKnowledgeSourceRequest request) {
        Long actorId = accessService.requireAcademicAdmin(principal).getId();
        validateUpload(request);
        byte[] originalFile;
        try {
            originalFile = request.getFile().getBytes();
        } catch (IOException exception) {
            throw new AiAssistantException(ErrorCode.INVALID_AI_DOCUMENT);
        }
        String fileHash = hashFile(originalFile);
        sourceRepository.findByFileHashAndDeletedFalse(fileHash).ifPresent(existingSource -> {
            throw new AiAssistantException(
                    ErrorCode.KNOWLEDGE_SOURCE_DUPLICATE,
                    "file",
                    "Tệp đã tồn tại ở nguồn #" + existingSource.getId() + ": " + existingSource.getTitle());
        });
        if (request.getPrevSourceId() != null) {
            KnowledgeSource previousSource = lockSource(request.getPrevSourceId());
            if (previousSource.getStatus() == KnowledgeSourceStatus.PROCESSING
                    || previousSource.getStatus() == KnowledgeSourceStatus.FAILED
                    || hasNewerSource(previousSource.getId())) {
                throw new AiAssistantException(ErrorCode.KNOWLEDGE_SOURCE_STATE_INVALID);
            }
        }
        // PROCESSING và tệp gốc cùng transaction; worker chỉ nhìn thấy nguồn sau commit.
        KnowledgeSource source = saveProcessingSource(actorId, request, fileHash, originalFile);
        // TODO(AUDIT_LOG): Lưu kết quả sau commit khi có audit; không lưu hash hoặc nội dung tệp vào log.
        log.info("Yêu cầu nạp nguồn tri thức: actorId={}, sourceId={}", actorId, source.getId());
        return response(source);
    }

    @Transactional(readOnly = true)
    public PageResponse<KnowledgeSourceResponse> findSources(Jwt principal, SearchKnowledgeSourcesRequest request) {
        accessService.requireAcademicAdmin(principal);
        validatePage(request.getPage(), request.getSize());
        var sources = sourceRepository.findAll(
                KnowledgeSourceSpecification.matches(request.getKeyword(), request.getStatus()),
                PageRequest.of(request.getPage(), request.getSize(), Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.<KnowledgeSourceResponse>builder()
                .content(sources.getContent().stream().map(this::response).toList())
                .pageNumber(sources.getNumber())
                .pageSize(sources.getSize())
                .totalElements(sources.getTotalElements())
                .totalPages(sources.getTotalPages())
                .isLast(sources.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public KnowledgeSourceResponse findSource(Jwt principal, Long sourceId) {
        accessService.requireAcademicAdmin(principal);
        return response(sourceRepository
                .findByIdAndDeletedFalse(sourceId)
                .orElseThrow(() -> new AiAssistantException(ErrorCode.KNOWLEDGE_SOURCE_NOT_FOUND)));
    }

    @Transactional
    public KnowledgeSourceResponse deactivateSource(Jwt principal, Long sourceId) {
        Long actorId = accessService.requireAcademicAdmin(principal).getId();
        deactivateDuePredecessors();
        KnowledgeSource source = lockSource(sourceId);
        if (source.getStatus() != KnowledgeSourceStatus.ACTIVE
                && source.getStatus() != KnowledgeSourceStatus.INACTIVE) {
            throw new AiAssistantException(ErrorCode.KNOWLEDGE_SOURCE_STATE_INVALID);
        }
        source.setStatus(KnowledgeSourceStatus.INACTIVE);
        log.info("Vô hiệu hóa nguồn tri thức: actorId={}, sourceId={}", actorId, sourceId);
        return response(source);
    }

    @Transactional
    public KnowledgeSourceResponse activateSource(Jwt principal, Long sourceId) {
        Long actorId = accessService.requireAcademicAdmin(principal).getId();
        KnowledgeSource source = lockSource(sourceId);
        if ((source.getStatus() != KnowledgeSourceStatus.INACTIVE && source.getStatus() != KnowledgeSourceStatus.ACTIVE)
                || !chunkPort.hasCompleteIndex(sourceId)
                || hasNewerSource(sourceId)
                || (source.getExpiryDate() != null && source.getExpiryDate().isBefore(LocalDate.now(clock)))) {
            throw new AiAssistantException(ErrorCode.KNOWLEDGE_SOURCE_STATE_INVALID);
        }
        source.setStatus(KnowledgeSourceStatus.ACTIVE);
        sourceRepository.flush();
        deactivateDuePredecessors();
        log.info("Kích hoạt nguồn tri thức: actorId={}, sourceId={}", actorId, sourceId);
        return response(source);
    }

    @Transactional
    public KnowledgeSourceResponse reprocessSource(Jwt principal, Long sourceId) {
        Long actorId = accessService.requireAcademicAdmin(principal).getId();
        KnowledgeSource source = lockSource(sourceId);
        if (source.getStatus() != KnowledgeSourceStatus.FAILED || sourceRepository.findOriginalFile(sourceId) == null) {
            throw new AiAssistantException(ErrorCode.KNOWLEDGE_SOURCE_STATE_INVALID);
        }
        chunkPort.deleteUnpublishedChunks(sourceId);
        source.setStatus(KnowledgeSourceStatus.PROCESSING);
        source.setProcessingToken(null);
        source.setProcessingStartedAt(null);
        source.setErrorMessage(null);
        source.setEmbeddingModel(properties.getEmbeddingModel());
        source.setEmbeddingModelVersion(properties.getEmbeddingModelVersion());
        log.info("Yêu cầu xử lý lại nguồn tri thức: actorId={}, sourceId={}", actorId, sourceId);
        return response(source);
    }

    @Transactional
    public void deleteSource(Jwt principal, Long sourceId) {
        Long actorId = accessService.requireAcademicAdmin(principal).getId();
        deactivateDuePredecessors();
        KnowledgeSource source = lockSource(sourceId);
        source.setStatus(KnowledgeSourceStatus.DELETED);
        source.setDeleted(true);
        sourceRepository.storeOriginalFile(sourceId, null);
        source.setProcessingToken(null);
        chunkPort.removeSourceVectors(sourceId);
        log.info("Xóa mềm nguồn tri thức và gỡ vector: actorId={}, sourceId={}", actorId, sourceId);
    }

    private KnowledgeSource lockSource(Long sourceId) {
        return sourceRepository
                .findForUpdate(sourceId)
                .filter(source -> !source.isDeleted())
                .orElseThrow(() -> new AiAssistantException(ErrorCode.KNOWLEDGE_SOURCE_NOT_FOUND));
    }

    private boolean hasNewerSource(Long sourceId) {
        return sourceRepository.existsByPrevSourceIdAndStatusInAndDeletedFalse(
                sourceId,
                List.of(
                        KnowledgeSourceStatus.PROCESSING,
                        KnowledgeSourceStatus.ACTIVE,
                        KnowledgeSourceStatus.INACTIVE));
    }

    private void deactivateDuePredecessors() {
        sourceRepository.deactivateSupersededSources(
                LocalDate.now(clock), LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    private void validateUpload(UploadKnowledgeSourceRequest request) {
        if (request == null
                || request.getFile() == null
                || request.getFile().isEmpty()
                || request.getFile().getOriginalFilename() == null
                || request.getTitle() == null
                || request.getTitle().isBlank()
                || request.getTitle().length() > 255
                || request.getDocumentType() == null
                || request.getDocumentType().isBlank()
                || request.getDocumentType().length() > 50
                || request.getEffectiveDate() == null
                || (request.getPrevSourceId() != null && request.getPrevSourceId() <= 0)) {
            throw new AiAssistantException(ErrorCode.VALIDATION_ERROR);
        }
        String filename = request.getFile().getOriginalFilename().trim().toLowerCase(Locale.ROOT);
        if (filename.length() > 255
                || !(filename.endsWith(".pdf") || filename.endsWith(".docx") || filename.endsWith(".txt"))) {
            throw new AiAssistantException(ErrorCode.INVALID_AI_DOCUMENT);
        }
        if (request.getFile().getSize() > properties.getMaxFileSize().toBytes()) {
            throw new AiAssistantException(ErrorCode.AI_DOCUMENT_TOO_LARGE);
        }
        if (request.getExpiryDate() != null && request.getExpiryDate().isBefore(request.getEffectiveDate())) {
            throw new AiAssistantException(
                    ErrorCode.VALIDATION_ERROR, "expiryDate", "Ngày hết hiệu lực không được trước ngày hiệu lực");
        }
    }

    private String hashFile(byte[] originalFile) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(originalFile));
        } catch (NoSuchAlgorithmException exception) {
            throw new AiAssistantException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new AiAssistantException(ErrorCode.VALIDATION_ERROR);
    }

    private KnowledgeSourceResponse response(KnowledgeSource source) {
        return new KnowledgeSourceResponse(
                source.getId(),
                source.getUploadedBy(),
                source.getPrevSourceId(),
                source.getTitle(),
                source.getDocumentType(),
                source.getOriginalFilename(),
                source.getUploadedAt(),
                source.getEffectiveDate(),
                source.getExpiryDate(),
                source.getStatus(),
                source.getErrorMessage(),
                source.getEmbeddingModel(),
                source.getEmbeddingModelVersion(),
                chunkPort.countChunks(source.getId()));
    }

    private KnowledgeSource saveProcessingSource(
            Long actorId, UploadKnowledgeSourceRequest request, String fileHash, byte[] originalFile) {
        KnowledgeSource source = sourceRepository.saveAndFlush(KnowledgeSource.builder()
                .uploadedBy(actorId)
                .prevSourceId(request.getPrevSourceId())
                .title(request.getTitle().trim())
                .documentType(request.getDocumentType().trim())
                .originalFilename(Objects.requireNonNull(request.getFile().getOriginalFilename())
                        .trim())
                .fileHash(fileHash)
                .uploadedAt(LocalDateTime.now(clock))
                .effectiveDate(request.getEffectiveDate())
                .expiryDate(request.getExpiryDate())
                .status(KnowledgeSourceStatus.PROCESSING)
                .embeddingModel(properties.getEmbeddingModel())
                .embeddingModelVersion(properties.getEmbeddingModelVersion())
                .build());
        sourceRepository.storeOriginalFile(source.getId(), originalFile);
        return source;
    }
}
