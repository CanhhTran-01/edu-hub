package com.eduhub.edu_hub.aiassistant.infrastructure.serialization;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.academic.api.dto.response.PrerequisiteImpactResponse;
import com.eduhub.edu_hub.academic.api.dto.response.PrerequisiteImpactResponse.*;
import com.eduhub.edu_hub.academic.domain.enums.*;
import com.eduhub.edu_hub.aiassistant.api.dto.response.AdvisorMessagePayloadResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.CourseGradeScaleResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.MinimumExamScoreResponse;
import com.eduhub.edu_hub.enrollment.domain.enums.MinimumExamScoreStatus;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AdvisorMessageContentCodecTest {
    AdvisorMessageContentCodec codec = new AdvisorMessageContentCodec(
            JsonMapper.builder().findAndAddModules().build());

    @Test
    void legacyTextAndOrdinaryJsonAreNotMisidentifiedAsTrustedPayloads() {
        for (String text : new String[] {"Tin cũ [K21]", "{nội dung chưa phải JSON}", "{\"text\":\"Ví dụ JSON\"}"}) {
            assertThat(codec.decode(text).text()).isEqualTo(text);
            assertThat(codec.decode(text).payload()).isNull();
        }
    }

    @Test
    void unknownVersionDisplaysItsTextWithoutReusingUnsupportedState() {
        var decoded =
                codec.decode("{\"schemaVersion\":99,\"type\":\"eduhub.advisor.message\",\"text\":\"Kết quả cũ\"}");
        assertThat(decoded.text()).isEqualTo("Kết quả cũ");
        assertThat(decoded.payload()).isNull();
    }

    @Test
    void supportedClarificationPreservesItsProvenance() {
        var payload = new AdvisorMessagePayloadResponse(
                1, AdvisorMessagePayloadResponse.TYPE, "Bạn muốn giữ điều kiện nào?", true, null, null);
        assertThat(codec.decode(codec.encode(payload)).payload()).isEqualTo(payload);
    }

    @Test
    void versionTwoPreservesNestedDependencyPathsUnknownHistoryAndTheScaleUsedForExamCalculation() {
        var calculatedAt = LocalDateTime.of(2026, 11, 27, 10, 0);
        var source = new SubjectReferenceResponse(1L, "GT1", "Giải tích 1");
        var dependent = new SubjectReferenceResponse(2L, "GT2", "Giải tích 2");
        var prerequisites = PrerequisiteImpactResponse.builder()
                .status(PrerequisiteAnalysisStatus.DATA_INCOMPLETE)
                .scope(PrerequisiteAnalysisScope.COURSE_IMPACT)
                .semester(
                        SemesterResponse.builder().id(3L).semesterName("Kỳ cũ").build())
                .courseReferences(List.of("GT1"))
                .pendingCourseReferences(List.of())
                .courses(List.of(new CourseImpactResponse(
                        null,
                        source,
                        false,
                        false,
                        false,
                        AcademicRequirementStatus.INSUFFICIENT_DATA,
                        List.of(new PrerequisiteResponse(dependent, null)),
                        true,
                        AcademicRequirementStatus.INSUFFICIENT_DATA,
                        List.of(new DependencyImpactResponse(
                                dependent, List.of(source, dependent), AcademicRequirementStatus.INSUFFICIENT_DATA)),
                        List.of())))
                .calculatedAt(calculatedAt)
                .build();
        var exam = MinimumExamScoreResponse.builder()
                .status(MinimumExamScoreStatus.SOLVED)
                .subjectName("Giải tích 1")
                .componentScore1(new BigDecimal("8"))
                .componentScore2(new BigDecimal("7"))
                .targetGradeLetter("A")
                .minimumExamScore(new BigDecimal("8.943"))
                .gradeScale(new CourseGradeScaleResponse(List.of(new CourseGradeScaleResponse.GradeBandResponse(
                        new BigDecimal("8.5"), "A", new BigDecimal("4")))))
                .calculatedAt(calculatedAt)
                .build();
        var payload = new AdvisorMessagePayloadResponse(
                2, AdvisorMessagePayloadResponse.TYPE, "Kết quả đã lưu.", false, null, null, prerequisites, exam);

        assertThat(codec.decode(codec.encode(payload)).payload()).isEqualTo(payload);
        assertThat(codec.decode(codec.encode(payload))
                        .payload()
                        .prerequisiteImpact()
                        .courses()
                        .getFirst()
                        .prerequisites()
                        .getFirst()
                        .passedBeforeSemester())
                .isNull();
    }
}
