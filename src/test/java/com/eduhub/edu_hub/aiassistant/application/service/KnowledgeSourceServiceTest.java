package com.eduhub.edu_hub.aiassistant.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.aiassistant.api.dto.request.UploadKnowledgeSourceRequest;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort;
import com.eduhub.edu_hub.aiassistant.domain.enums.KnowledgeSourceStatus;
import com.eduhub.edu_hub.aiassistant.domain.model.KnowledgeSource;
import com.eduhub.edu_hub.aiassistant.domain.repository.KnowledgeSourceRepository;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;

class KnowledgeSourceServiceTest {
    AiAccessService accessService = mock(AiAccessService.class);
    KnowledgeSourceRepository sources = mock(KnowledgeSourceRepository.class);
    KnowledgeChunkPort chunks = mock(KnowledgeChunkPort.class);
    AiRagProperties properties = new AiRagProperties();
    Clock clock = Clock.fixed(Instant.parse("2026-11-18T17:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    KnowledgeSourceService service = new KnowledgeSourceService(accessService, sources, chunks, properties, clock);
    Jwt principal =
            Jwt.withTokenValue("token").header("alg", "HS256").subject("admin").build();

    @BeforeEach
    void authenticate() {
        when(accessService.requireAcademicAdmin(principal))
                .thenReturn(UserInfoResponse.builder().id(7L).build());
    }

    @Test
    void duplicateHashDoesNotCreateVersionOrModifyExistingIndex() {
        KnowledgeSource existing = KnowledgeSource.builder().title("Quy chế").build();
        existing.setId(8L);
        when(sources.findByFileHashAndDeletedFalse(anyString())).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.upload(principal, uploadRequest()))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.KNOWLEDGE_SOURCE_DUPLICATE));
        verify(sources, never()).saveAndFlush(any());
        verifyNoInteractions(chunks);
    }

    @Test
    void uploadStoresOriginalAndQueuesProcessingWithoutEmbedding() {
        when(sources.findByFileHashAndDeletedFalse(anyString())).thenReturn(Optional.empty());
        when(sources.saveAndFlush(any())).thenAnswer(invocation -> {
            KnowledgeSource source = invocation.getArgument(0);
            source.setId(8L);
            return source;
        });
        var response = service.upload(principal, uploadRequest());
        assertThat(response.status()).isEqualTo(KnowledgeSourceStatus.PROCESSING);
        assertThat(response.uploadedBy()).isEqualTo(7L);
        assertThat(response.uploadedAt()).isEqualTo(LocalDateTime.of(2026, 11, 19, 0, 0));
        verify(sources).storeOriginalFile(eq(8L), any(byte[].class));
        verify(chunks, never()).storeChunks(anyLong(), anyString(), anyList());
    }

    @Test
    void deleteKeepsSourceMetadataAndOnlyRemovesFileAndVectors() {
        var source = KnowledgeSource.builder()
                .title("Quy chế")
                .status(KnowledgeSourceStatus.ACTIVE)
                .build();
        source.setId(8L);
        when(sources.findForUpdate(8L)).thenReturn(Optional.of(source));
        service.deleteSource(principal, 8L);
        assertThat(source.isDeleted()).isTrue();
        assertThat(source.getStatus()).isEqualTo(KnowledgeSourceStatus.DELETED);
        assertThat(source.getTitle()).isEqualTo("Quy chế");
        verify(sources).storeOriginalFile(8L, null);
        verify(chunks).removeSourceVectors(8L);
        verify(sources, never()).delete(any(KnowledgeSource.class));
    }

    private UploadKnowledgeSourceRequest uploadRequest() {
        var request = new UploadKnowledgeSourceRequest();
        request.setFile(new MockMultipartFile(
                "file",
                "quy-che.txt",
                "text/plain",
                "Quy chế đào tạo".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        request.setTitle("Quy chế đào tạo");
        request.setDocumentType("Quy chế");
        request.setEffectiveDate(LocalDate.of(2026, 1, 1));
        return request;
    }

    @ParameterizedTest
    @EnumSource(
            value = KnowledgeSourceStatus.class,
            names = {"PROCESSING", "ACTIVE", "INACTIVE", "FAILED"})
    void downloadReturnsOriginalBytesEvenWhenSourceIsInactiveFailedOrExpired(KnowledgeSourceStatus status) {
        KnowledgeSource source = KnowledgeSource.builder()
                .originalFilename("Quy chế đào tạo.PDF")
                .status(status)
                .expiryDate(LocalDate.of(2025, 1, 1))
                .build();
        byte[] originalFile = new byte[] {0, 1, -1, 2};
        when(sources.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(source));
        when(sources.findOriginalFile(8L)).thenReturn(originalFile);
        var download = service.downloadSourceFile(principal, 8L);
        assertThat(download.filename()).isEqualTo("Quy chế đào tạo.PDF");
        assertThat(download.contentType()).isEqualTo("application/pdf");
        assertThat(download.fileBytes()).containsExactly(originalFile);
        verify(accessService).requireAcademicAdmin(principal);
        verifyNoInteractions(chunks);
        verify(sources, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({
        "document.docx, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "document.txt, text/plain;charset=UTF-8",
        "document.bin, application/octet-stream"
    })
    void downloadUsesTheFileFormatRatherThanTheBusinessDocumentType(String filename, String contentType) {
        KnowledgeSource source = KnowledgeSource.builder()
                .originalFilename(filename)
                .documentType("Quy chế")
                .build();
        when(sources.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(source));
        when(sources.findOriginalFile(8L)).thenReturn(new byte[] {1});
        assertThat(service.downloadSourceFile(principal, 8L).contentType()).isEqualTo(contentType);
    }

    @Test
    void downloadRemovesPathsAndHeaderControlCharactersFromTheFilename() {
        KnowledgeSource source = KnowledgeSource.builder()
                .originalFilename("C:\\uploads\\Quy chế\r\n.pdf")
                .build();
        when(sources.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(source));
        when(sources.findOriginalFile(8L)).thenReturn(new byte[] {1});
        assertThat(service.downloadSourceFile(principal, 8L).filename()).isEqualTo("Quy chế__.pdf");
    }

    @Test
    void downloadRejectsMissingOrDeletedSourceBeforeReadingTheFile() {
        assertThatThrownBy(() -> service.downloadSourceFile(principal, 8L))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.KNOWLEDGE_SOURCE_NOT_FOUND));
        verify(sources, never()).findOriginalFile(any());
    }

    @Test
    void downloadReportsLegacySourcesWithoutOriginalFilesAsNotFound() {
        when(sources.findByIdAndDeletedFalse(8L))
                .thenReturn(Optional.of(KnowledgeSource.builder().build()));
        assertThatThrownBy(() -> service.downloadSourceFile(principal, 8L))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.KNOWLEDGE_SOURCE_FILE_NOT_FOUND));
    }

    @Test
    void downloadChecksAccessBeforeAnyFileOrMetadataRead() {
        when(accessService.requireAcademicAdmin(principal)).thenThrow(new AiAssistantException(ErrorCode.UNAUTHORIZED));
        assertThatThrownBy(() -> service.downloadSourceFile(principal, 8L)).isInstanceOf(AiAssistantException.class);
        verifyNoInteractions(sources, chunks);
    }
}
