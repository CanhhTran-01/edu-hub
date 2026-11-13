package com.eduhub.edu_hub.aiassistant.application.service;

import com.eduhub.edu_hub.aiassistant.api.dto.response.DocumentUploadResponse;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.DocumentChunkingPort;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeDocumentPort;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DocumentIngestionService {
    private final AiAccessService accessService;
    private final DocumentChunkingPort chunkingPort;
    private final KnowledgeDocumentPort documentPort;
    private final AiRagProperties properties;

    public DocumentUploadResponse upload(Jwt jwt, MultipartFile file) {
        accessService.requireAcademicAdmin(jwt);
        validate(file);
        String sourceTitle = file.getOriginalFilename().trim();
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            if (content.isBlank()) throw new AiAssistantException(ErrorCode.INVALID_AI_DOCUMENT);
            List<KnowledgeDocumentPort.KnowledgeChunk> chunks = chunkingPort.chunk(content);
            if (chunks.isEmpty()) throw new AiAssistantException(ErrorCode.INVALID_AI_DOCUMENT);
            documentPort.replace(sourceTitle, chunks);
            return new DocumentUploadResponse(sourceTitle, chunks.size());
        } catch (IOException exception) {
            throw new AiAssistantException(ErrorCode.INVALID_AI_DOCUMENT);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null) {
            throw new AiAssistantException(ErrorCode.INVALID_AI_DOCUMENT);
        }
        String filename = file.getOriginalFilename().trim().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".txt")) throw new AiAssistantException(ErrorCode.INVALID_AI_DOCUMENT);
        if (file.getSize() > properties.getMaxFileSize().toBytes()) {
            throw new AiAssistantException(ErrorCode.AI_DOCUMENT_TOO_LARGE);
        }
    }
}
