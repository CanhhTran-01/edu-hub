package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.*;
import com.eduhub.edu_hub.aiassistant.application.service.KnowledgeSourceIngestionService;
import com.eduhub.edu_hub.aiassistant.application.service.KnowledgeSourceIngestionService.IngestionTask;
import com.eduhub.edu_hub.aiassistant.domain.policy.AiContentPolicy;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import java.util.ArrayList;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/** Một worker nội bộ nhận nguồn đã commit; token trong DB giữ lần xử lý cũ không ghi đè lần mới. */
@Component
@Slf4j
public class KnowledgeSourceIngestionWorker {
    private final KnowledgeSourceIngestionService ingestionService;
    private final KnowledgeDocumentTextExtractor textExtractor;
    private final DocumentChunkingPort chunkingPort;
    private final KnowledgeChunkPort chunkPort;
    private final AiRagProperties properties;
    private final ThreadPoolTaskExecutor executor;

    public KnowledgeSourceIngestionWorker(
            KnowledgeSourceIngestionService ingestionService,
            KnowledgeDocumentTextExtractor textExtractor,
            DocumentChunkingPort chunkingPort,
            KnowledgeChunkPort chunkPort,
            AiRagProperties properties,
            @Qualifier("knowledgeIngestionExecutor") ThreadPoolTaskExecutor executor) {
        this.ingestionService = ingestionService;
        this.textExtractor = textExtractor;
        this.chunkingPort = chunkingPort;
        this.chunkPort = chunkPort;
        this.properties = properties;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${eduhub.ai.rag.worker-delay-ms:5000}", scheduler = "knowledgeIngestionScheduler")
    public void processPendingSources() {
        try {
            ingestionService.recoverInterruptedSources();
            if (executor.getActiveCount() > 0) return;
            for (Long sourceId : ingestionService.findPendingSources()) {
                IngestionTask task = ingestionService.claimSource(sourceId);
                if (task == null) continue;
                Future<?> future = null;
                try {
                    future = executor.submit(() -> processSource(task));
                    future.get(properties.getIngestionTimeout().toMillis(), TimeUnit.MILLISECONDS);
                    return;
                } catch (InterruptedException exception) {
                    future.cancel(true);
                    Thread.currentThread().interrupt();
                    ingestionService.failSource(task, "Tác vụ bị gián đoạn. Vui lòng yêu cầu xử lý lại.");
                    return;
                } catch (TimeoutException | ExecutionException | RejectedExecutionException exception) {
                    if (future != null) future.cancel(true);
                    ingestionService.failSource(
                            task, "Dịch vụ xử lý tài liệu lỗi hoặc quá thời hạn. Vui lòng xử lý lại.");
                    return;
                }
            }
        } catch (RuntimeException exception) {
            // Không ghi exception của provider vì có thể chứa nội dung tệp hoặc thông tin xác thực.
            log.warn("Chưa thể nhận hoặc cập nhật tác vụ nguồn tri thức");
        }
    }

    private void processSource(IngestionTask task) {
        try {
            var pages = textExtractor.extractPages(task.filename(), task.originalFile());
            var chunks = new ArrayList<KnowledgeChunkPort.ChunkContent>();
            for (var page : pages) {
                if (AiContentPolicy.containsSensitiveDocumentContent(page.content())) {
                    ingestionService.failSource(
                            task,
                            "Phát hiện mẫu định danh cá nhân hoặc thông tin xác thực. Chỉ nạp tài liệu quy chế phù hợp.");
                    return;
                }
                chunks.addAll(chunkingPort.chunk(page.content(), page.pageNumber()));
                if (chunks.size() > properties.getMaximumChunks()) {
                    ingestionService.failSource(task, "Tài liệu vượt giới hạn số đoạn xử lý. Vui lòng tách tài liệu.");
                    return;
                }
            }
            if (chunks.isEmpty()) {
                ingestionService.failSource(
                        task, "Không trích xuất được văn bản. PDF dạng ảnh scan chưa được hỗ trợ OCR.");
                return;
            }
            chunkPort.storeChunks(task.sourceId(), task.processingToken(), chunks);
            ingestionService.completeSource(task, chunks.size());
        } catch (AiAssistantException exception) {
            ingestionService.failSource(
                    task, "Không đọc được văn bản PDF/DOCX/TXT. Kiểm tra tệp hỏng, mã hóa UTF-8 hoặc PDF scan.");
        } catch (RuntimeException exception) {
            ingestionService.failSource(
                    task, "Dịch vụ embedding hoặc lưu chỉ mục thất bại. Vui lòng yêu cầu xử lý lại.");
        }
    }
}
