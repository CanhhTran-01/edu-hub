package com.eduhub.edu_hub.aiassistant.application.service;

import com.eduhub.edu_hub.academic.api.dto.response.AdvisorAcademicProfileResponse;
import com.eduhub.edu_hub.academic.application.facade.AcademicFacade;
import com.eduhub.edu_hub.aiassistant.domain.policy.AiContentPolicy;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Chỉ hai capability đọc; principal được giữ trong execution phía server, không nằm trong arguments của model. */
@Service
@RequiredArgsConstructor
public class AdvisorToolService {
    private final AiAccessService accessService;
    private final AcademicFacade.Advisor academicFacade;
    private final AcademicKnowledgeSearchService knowledgeSearchService;

    public AdvisorAcademicProfileResponse getAcademicProfile(AdvisorChatExecution execution) {
        accessService.requireStudent(execution.getPrincipal());
        var profile = academicFacade.findMyAcademicProfile(execution.getPrincipal());
        execution.recordAcademicProfile(profile);
        return profile;
    }

    public List<KnowledgePassage> searchAcademicKnowledge(AdvisorChatExecution execution, String query) {
        accessService.requireStudent(execution.getPrincipal());
        var chunks = knowledgeSearchService.findRelevantKnowledge(query);
        execution.recordKnowledgeChunks(chunks);
        return chunks.stream()
                .map(chunk -> new KnowledgePassage(
                        "K" + chunk.chunkId(),
                        chunk.chunkId(),
                        chunk.sourceTitle(),
                        chunk.pageNumber(),
                        chunk.effectiveDate(),
                        AiContentPolicy.redactPromptText(chunk.content())))
                .toList();
    }

    public record KnowledgePassage(
            String referenceCode,
            Long chunkId,
            String sourceTitle,
            Integer pageNumber,
            LocalDate effectiveDate,
            String content) {}
}
