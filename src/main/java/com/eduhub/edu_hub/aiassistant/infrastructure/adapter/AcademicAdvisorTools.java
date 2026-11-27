package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import com.eduhub.edu_hub.academic.api.dto.request.GpaGoalRequest.CourseGradeConditionRequest;
import com.eduhub.edu_hub.academic.api.dto.response.AdvisorAcademicProfileResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaGoalResponse;
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
    static final String GPA_GOAL_TOOL = "solveGpaGoal";

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
            name = GPA_GOAL_TOOL,
            description =
                    "Tìm phương án GPA kỳ hiện tại theo mục tiêu đã lưu tại Profile. "
                            + "BE tự lấy kỳ, môn, thang và mục tiêu. Gửi mọi điều kiện mới trong cùng lần gọi; "
                            + "EXACT là đúng điểm, AT_MOST là tối đa, AT_LEAST là ít nhất, REMOVE gỡ điều kiện môn, "
                            + "UNSPECIFIED khi câu 'đạt B' chưa rõ nghĩa để BE hỏi lại. Không tự đoán môn hoặc mức điểm. "
                            + "Các điều kiện cũ được giữ, môn có thay đổi thay toàn bộ điều kiện cũ của môn đó. "
                            + "Khi trả lời câu hỏi làm rõ, gửi đủ pendingConditions đã được làm rõ. Không lấy điểm phương án cũ làm điều kiện.")
    public GpaGoalResponse solveGpaGoal(
            @ToolParam(
                            description = "Các điều kiện cần thêm/đổi/gỡ; [] khi không thay đổi. Mỗi điều kiện có "
                                    + "courseReference là mã/tên môn, enrollmentId chỉ dùng khi BE đã cung cấp, "
                                    + "comparison và gradeLetter; REMOVE không cần gradeLetter.")
                    List<CourseGradeConditionRequest> conditionChanges,
            @ToolParam(
                            description =
                                    "Chỉ true khi sinh viên yêu cầu bỏ toàn bộ điều kiện hoặc bắt đầu lại ở kỳ hiện tại.")
                    boolean resetConditions) {
        return toolService.solveGpaGoal(execution, conditionChanges, resetConditions);
    }

    @Tool(
            name = KNOWLEDGE_TOOL,
            description = "Tra cứu quy chế, quy định và thủ tục học vụ, trả đoạn trích cùng mã nguồn. "
                    + "Dựng query đầy đủ từ ngữ cảnh; thiếu đối tượng thì hỏi rõ, không tự đoán.")
    public List<KnowledgePassage> searchAcademicKnowledge(
            @ToolParam(description = "Câu hỏi tra cứu đầy đủ ngữ cảnh, không trống và tối đa 2.000 ký tự.")
                    String query) {
        return toolService.searchAcademicKnowledge(execution, query);
    }
}
