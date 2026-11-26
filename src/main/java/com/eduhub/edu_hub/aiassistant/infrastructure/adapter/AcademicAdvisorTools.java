package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import com.eduhub.edu_hub.academic.api.dto.response.AdvisorAcademicProfileResponse;
import com.eduhub.edu_hub.aiassistant.application.service.AdvisorChatExecution;
import com.eduhub.edu_hub.aiassistant.application.service.AdvisorToolService;
import com.eduhub.edu_hub.aiassistant.application.service.AdvisorToolService.KnowledgePassage;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Khai báo các tool học vụ cho Spring AI. Mỗi lượt chat tạo một instance riêng để giữ execution và principal
 * phía server, không đưa chúng vào tham số do model cung cấp hoặc chia sẻ giữa các sinh viên.
 */
public final class AcademicAdvisorTools {
    static final String PROFILE_TOOL = "getAcademicProfile";
    static final String KNOWLEDGE_TOOL = "searchAcademicKnowledge";

    private final AdvisorToolService toolService;
    private final AdvisorChatExecution execution;

    public AcademicAdvisorTools(AdvisorToolService toolService, AdvisorChatExecution execution) {
        this.toolService = toolService;
        this.execution = execution;
    }

    @Tool(
            name = PROFILE_TOOL,
            description =
                    "Đọc GPA, tín chỉ, mục tiêu và độ đầy đủ lịch sử của chính sinh viên hiện tại. Không nhận danh tính.")
    public AdvisorAcademicProfileResponse getAcademicProfile() {
        return toolService.getAcademicProfile(execution);
    }

    @Tool(
            name = KNOWLEDGE_TOOL,
            description = "Tra cứu quy chế, quy định và thủ tục học vụ, trả đoạn trích cùng mã nguồn. "
                    + "Dựng query đầy đủ từ ngữ cảnh; thiếu đối tượng thì hỏi rõ, không tự đoán.")
    public List<KnowledgePassage> searchAcademicKnowledge(
            @ToolParam(description = "Câu hỏi tra cứu đầy đủ ngữ cảnh, không trống và tối đa 2.000 ký tự.") String query) {
        return toolService.searchAcademicKnowledge(execution, query);
    }
}
