package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import com.eduhub.edu_hub.academic.api.dto.request.GpaGoalRequest.CourseGradeConditionRequest;
import com.eduhub.edu_hub.academic.api.dto.response.AdvisorAcademicProfileResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaGoalResponse;
import com.eduhub.edu_hub.academic.api.dto.response.PrerequisiteImpactResponse;
import com.eduhub.edu_hub.academic.domain.enums.PrerequisiteAnalysisScope;
import com.eduhub.edu_hub.aiassistant.application.service.AdvisorChatExecution;
import com.eduhub.edu_hub.aiassistant.application.service.AdvisorToolService;
import com.eduhub.edu_hub.aiassistant.application.service.AdvisorToolService.KnowledgePassage;
import com.eduhub.edu_hub.enrollment.api.dto.response.MinimumExamScoreResponse;
import java.math.BigDecimal;
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
    static final String PREREQUISITE_TOOL = "analyzePrerequisiteImpact";
    static final String EXAM_SCORE_TOOL = "solveExamScore";

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
            name = PREREQUISITE_TOOL,
            description = "Đọc tiên quyết của sinh viên theo dữ liệu thật ở kỳ hiện tại. "
                    + "SEMESTER_OVERVIEW phân tích độc lập mọi môn đã đăng ký, courseReferences=[]. "
                    + "COURSE_IMPACT phân tích một hoặc nhiều mã/tên môn trong CTĐT hoặc kỳ hiện tại. "
                    + "Phân biệt điều kiện trước kỳ với ảnh hưởng trực tiếp/gián tiếp khi không đạt; "
                    + "môn đã đạt không bị mất và chặn lan truyền. Thiếu/mơ hồ trả câu hỏi làm rõ, không đoán. "
                    + "Không gán F toàn kỳ, không sửa điều kiện GPA, không khẳng định lùi kỳ/ngày tốt nghiệp.")
    public PrerequisiteImpactResponse analyzePrerequisiteImpact(
            @ToolParam(description = "SEMESTER_OVERVIEW hoặc COURSE_IMPACT.") PrerequisiteAnalysisScope scope,
            @ToolParam(
                            description =
                                    "Mã/tên môn cần phân tích; [] cho toàn kỳ. Dựng đầy đủ tham chiếu từ ngữ cảnh khi hỏi tiếp.")
                    List<String> courseReferences) {
        return toolService.analyzePrerequisiteImpact(execution, scope, courseReferences);
    }

    @Tool(
            name = EXAM_SCORE_TOOL,
            description = "Tính điểm thi tối thiểu để đạt ngưỡng điểm chữ từ TP1 và TP2 người dùng nhập. "
                    + "Đây là tính thử, tên môn chỉ là nhãn, không bắt buộc môn đang đăng ký; không tự lấy điểm thật thay đầu vào. "
                    + "BE dùng thang và phép tính/làm tròn Enrollment, trả ngưỡng 0,001 và mức an toàn cho hai chữ số thập phân. "
                    + "Để field thiếu là null, không tự điền 0 hoặc tự suy điểm; còn dấu ... thì hỏi lại. "
                    + "Khi hỏi tiếp cùng môn, chỉ truyền phần thay đổi và resetInputs=false. "
                    + "Khi bắt đầu câu hỏi/mẫu tính mới, resetInputs=true; đổi môn cần gửi đủ điểm.")
    public MinimumExamScoreResponse solveExamScore(
            @ToolParam(
                            description = "Tên môn do người dùng nhập; null khi giữ môn của lần tính trước.",
                            required = false)
                    String subjectName,
            @ToolParam(
                            description =
                                    "TP1 dạng số, 0..10, tối đa ba chữ số thập phân; null nếu chưa có hoặc giữ đầu vào cũ.",
                            required = false)
                    BigDecimal componentScore1,
            @ToolParam(
                            description =
                                    "TP2 dạng số, 0..10, tối đa ba chữ số thập phân; null nếu chưa có hoặc giữ đầu vào cũ.",
                            required = false)
                    BigDecimal componentScore2,
            @ToolParam(
                            description = "Điểm chữ mục tiêu, như A, A+, B; null nếu thiếu hoặc giữ mục tiêu cũ.",
                            required = false)
                    String targetGradeLetter,
            @ToolParam(
                            description =
                                    "true khi bắt đầu mẫu tính mới; false khi tiếp tục cùng môn và giữ các đầu vào khác.")
                    boolean resetInputs) {
        return toolService.solveExamScore(
                execution, subjectName, componentScore1, componentScore2, targetGradeLetter, resetInputs);
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
