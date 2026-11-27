package com.eduhub.edu_hub.enrollment.application.service;

import com.eduhub.edu_hub.enrollment.api.dto.request.MinimumExamScoreRequest;
import com.eduhub.edu_hub.enrollment.api.dto.response.CourseGradeScaleResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.MinimumExamScoreResponse;
import com.eduhub.edu_hub.enrollment.domain.enums.MinimumExamScoreStatus;
import com.eduhub.edu_hub.enrollment.domain.policy.CourseGradePolicy;
import com.eduhub.edu_hub.enrollment.domain.policy.MinimumExamScorePolicy;
import com.eduhub.edu_hub.enrollment.domain.policy.ProcessScorePolicy;
import com.eduhub.edu_hub.enrollment.infrastructure.config.CourseGradeSettings;
import com.eduhub.edu_hub.shared.common.TextsNormalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Tính thử trên đầu vào và cấu hình Enrollment, không truy vấn hoặc ghi điểm thật. Consumer kiểm tra danh tính. */
@Service
@RequiredArgsConstructor
public class MinimumExamScoreService {
    private final CourseGradeSettings settings;
    private final Clock clock;

    public MinimumExamScoreResponse solveMinimumExamScore(MinimumExamScoreRequest request) {
        var response = MinimumExamScoreResponse.builder().calculatedAt(LocalDateTime.now(clock));
        if (request == null) return clarification(response, "Hãy điền tên môn, TP1, TP2 và mức điểm chữ muốn đạt.");
        String subjectName = TextsNormalizer.normalizeOptionalText(request.subjectName());
        String targetLetter = TextsNormalizer.normalizeKey(request.targetGradeLetter());
        response.subjectName(subjectName)
                .componentScore1(request.componentScore1())
                .componentScore2(request.componentScore2())
                .targetGradeLetter(targetLetter.isEmpty() ? null : targetLetter);
        if (subjectName == null
                || subjectName.contains("...")
                || subjectName.contains("…")
                || request.componentScore1() == null
                || request.componentScore2() == null
                || targetLetter.isEmpty()
                || targetLetter.contains("...")
                || targetLetter.contains("…"))
            return clarification(
                    response,
                    "Hãy điền đủ tên môn, TP1, TP2 và điểm chữ mục tiêu; không để lại dấu ... trong mẫu câu.");
        if (subjectName.length() > 255) return clarification(response, "Tên môn không được vượt quá 255 ký tự.");
        if (!ProcessScorePolicy.isValidComponentScore(request.componentScore1())
                || !ProcessScorePolicy.isValidComponentScore(request.componentScore2()))
            return clarification(
                    response, "TP1 và TP2 phải từ 0 đến 10, tối đa ba chữ số thập phân; 0 là điểm đã nhập.");
        var scale = CourseGradePolicy.requireValidScale(settings.getGradeScale());
        response.gradeScale(new CourseGradeScaleResponse(scale.stream()
                .map(band -> new CourseGradeScaleResponse.GradeBandResponse(
                        band.minimumScore(), band.letter(), band.gradePoint()))
                .toList()));
        var target = scale.stream()
                .filter(band -> band.letter().equals(targetLetter))
                .findFirst()
                .orElse(null);
        if (target == null)
            return clarification(
                    response,
                    "Mức điểm mục tiêu không có trong thang hiện hành. Hãy chọn: "
                            + scale.stream()
                                    .map(CourseGradePolicy.GradeBand::letter)
                                    .collect(Collectors.joining(", ")) + ".");
        var calculation =
                MinimumExamScorePolicy.solve(request.componentScore1(), request.componentScore2(), targetLetter, scale);
        response.processScore(calculation.processScore())
                .targetMinimumScore10(target.minimumScore())
                .maximumCourseScore10(calculation.maximumGrade().score10())
                .maximumGradeLetter(calculation.maximumGrade().letter());
        if (!calculation.reachable())
            return response.status(MinimumExamScoreStatus.UNREACHABLE)
                    .message("Không thể đạt ngưỡng mục tiêu với TP1/TP2 đã nhập, kể cả điểm thi bằng 10.")
                    .build();
        return response.status(MinimumExamScoreStatus.SOLVED)
                .minimumExamScore(calculation.minimumExamScore())
                .minimumExamScoreTwoDecimals(calculation.minimumExamScoreTwoDecimals())
                .achievedCourseScore10(calculation.achievedGrade().score10())
                .achievedGradeLetter(calculation.achievedGrade().letter())
                .message(
                        "Đã tính và kiểm tra điểm thi tối thiểu theo bước 0,001. Đây là phép tính thử từ điểm bạn nhập.")
                .build();
    }

    private MinimumExamScoreResponse clarification(
            MinimumExamScoreResponse.MinimumExamScoreResponseBuilder response, String message) {
        return response.status(MinimumExamScoreStatus.NEEDS_CLARIFICATION)
                .message(message)
                .build();
    }
}
