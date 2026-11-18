package com.eduhub.edu_hub.aiassistant.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort;
import com.eduhub.edu_hub.aiassistant.application.service.KnowledgeSourceIngestionService.IngestionTask;
import com.eduhub.edu_hub.aiassistant.domain.enums.KnowledgeSourceStatus;
import com.eduhub.edu_hub.aiassistant.domain.model.KnowledgeSource;
import com.eduhub.edu_hub.aiassistant.domain.repository.KnowledgeSourceRepository;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.*;

class KnowledgeSourceIngestionServiceTest {
    KnowledgeSourceRepository repository = mock(KnowledgeSourceRepository.class);
    KnowledgeChunkPort chunkPort = mock(KnowledgeChunkPort.class);
    Clock clock = Clock.fixed(Instant.parse("2026-11-18T17:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    KnowledgeSourceIngestionService service =
            new KnowledgeSourceIngestionService(repository, chunkPort, new AiRagProperties(), clock);
    KnowledgeSource source;
    IngestionTask task = new IngestionTask(9L, "current-attempt", "quy-che.txt", new byte[] {1});

    @BeforeEach
    void prepareSource() {
        source = KnowledgeSource.builder()
                .title("Quy chế")
                .status(KnowledgeSourceStatus.PROCESSING)
                .processingToken("current-attempt")
                .prevSourceId(1L)
                .build();
        source.setId(9L);
        when(repository.findForUpdate(9L)).thenReturn(Optional.of(source));
    }

    @Test
    void activatesOnlyCompleteIndexAndThenDisablesDuePreviousVersion() {
        when(chunkPort.countChunks(9L)).thenReturn(3L);
        when(chunkPort.hasCompleteIndex(9L)).thenReturn(true);
        service.completeSource(task, 3);
        assertThat(source.getStatus()).isEqualTo(KnowledgeSourceStatus.ACTIVE);
        assertThat(source.getProcessingToken()).isNull();
        var order = inOrder(chunkPort, repository);
        order.verify(chunkPort).countChunks(9L);
        order.verify(chunkPort).hasCompleteIndex(9L);
        order.verify(repository).flush();
        order.verify(repository)
                .deactivateSupersededSources(LocalDate.of(2026, 11, 19), LocalDateTime.of(2026, 11, 18, 17, 0));
    }

    @Test
    void incompleteIndexCannotActivateOrReplacePreviousSource() {
        when(chunkPort.countChunks(9L)).thenReturn(2L);
        assertThatThrownBy(() -> service.completeSource(task, 3)).isInstanceOf(IllegalStateException.class);
        assertThat(source.getStatus()).isEqualTo(KnowledgeSourceStatus.PROCESSING);
        verify(repository, never()).deactivateSupersededSources(any(), any());
    }

    @Test
    void failedEmbeddingCleansUnpublishedChunksAndKeepsPreviousSource() {
        service.failSource(task, "Dịch vụ embedding thất bại.");
        verify(chunkPort).deleteUnpublishedChunks(9L);
        assertThat(source.getStatus()).isEqualTo(KnowledgeSourceStatus.FAILED);
        assertThat(source.getErrorMessage()).isEqualTo("Dịch vụ embedding thất bại.");
        verify(repository, never()).deactivateSupersededSources(any(), any());
    }

    @Test
    void staleWorkerCannotCompleteOrFailNewerAttempt() {
        source.setProcessingToken("new-attempt");
        service.completeSource(task, 3);
        service.failSource(task, "Lỗi của worker cũ.");
        assertThat(source.getProcessingToken()).isEqualTo("new-attempt");
        assertThat(source.getStatus()).isEqualTo(KnowledgeSourceStatus.PROCESSING);
        verifyNoInteractions(chunkPort);
    }

    @Test
    void deletedSourceIsNotActivatedByLateWorker() {
        source.setDeleted(true);
        source.setStatus(KnowledgeSourceStatus.DELETED);
        service.completeSource(task, 3);
        verifyNoInteractions(chunkPort);
        assertThat(source.getStatus()).isEqualTo(KnowledgeSourceStatus.DELETED);
    }
}
