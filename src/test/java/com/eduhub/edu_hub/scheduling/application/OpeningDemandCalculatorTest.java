package com.eduhub.edu_hub.scheduling.application;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.curriculum.application.facade.dto.CurriculumOpeningSubjectResponse;
import com.eduhub.edu_hub.scheduling.application.port.PlanningSnapshot;
import com.eduhub.edu_hub.scheduling.application.port.PlanningSnapshot.*;
import com.eduhub.edu_hub.scheduling.infrastructure.PlanningTestData;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OpeningDemandCalculatorTest {
    private final OpeningDemandCalculator calculator = new OpeningDemandCalculator();

    @Test
    void fixtureCountsSharedSubjectOncePerStudentAndSeparatesDebtFromRecommendedStudy() {
        var data = PlanningTestData.load();
        var plan = List.of(
                subject("CNTT_4Y_V1", 1), subject("ATTT_4Y_V1", 1), subject("DTVT_4Y_V1", 1), subject("TTNT_4Y_V1", 1));

        var result = calculator.calculate("HK_1_2026_2027", plan, data);

        assertThat(result).singleElement().satisfies(demand -> {
            assertThat(demand.getOnTimeStudentCount()).isEqualTo(97);
            assertThat(demand.getRetakeStudentCount()).isEqualTo(2);
            assertThat(demand.getMissedStudentCount()).isEqualTo(2);
            assertThat(demand.getExpectedStudentCount()).isEqualTo(101);
            assertThat(demand.getRecommendedClassCount()).isEqualTo(2);
            assertThat(demand.getDataSource()).isEqualTo("TEST_DATA");
            assertThat(demand.isHistoryComplete()).isTrue();
        });
        // Fixture gồm: đạt rồi rớt khi cải thiện, rớt rồi đạt, đang học, đã hủy,
        // SV quá 8 kỳ, kết quả ở kỳ tương lai, SV ngừng học và khóa chưa nhập học.
        assertThat(data.students()).hasSize(107);
    }

    @Test
    void onlyStudyingStudentsContributeRegardlessOfTheirAccountOrExpectedGraduationYear() {
        var data = new PlanningSnapshot(
                "TEST",
                true,
                List.of(
                        new Student(1, "SV01", "CNTT_4Y_V1", 2020, ActivityStatus.STUDYING),
                        new Student(2, "SV02", "CNTT_4Y_V1", 2020, ActivityStatus.DEACTIVATED),
                        new Student(3, "SV03", "CNTT_4Y_V1", 2020, ActivityStatus.DELETED)),
                List.of());
        var result = calculator.calculate("HK_1_2026_2027", List.of(subject("CNTT_4Y_V1", 1)), data);
        assertThat(result).singleElement().satisfies(demand -> {
            assertThat(demand.getMissedStudentCount()).isEqualTo(1);
            assertThat(demand.getExpectedStudentCount()).isEqualTo(1);
        });
    }

    @Test
    void secondSemesterUsesAcademicYearInsteadOfCalendarYearOfStartDate() {
        var data = snapshot(true, List.of());
        var result = calculator.calculate("HK_2_2026_2027", List.of(subject("CNTT_4Y_V1", 2)), data);
        assertThat(result).singleElement().satisfies(demand -> {
            assertThat(demand.getOnTimeStudentCount()).isEqualTo(1);
            assertThat(demand.getMissedStudentCount()).isZero();
        });
    }

    @Test
    void missingHistoryIsUnavailableInsteadOfInferringThatEveryoneMissedTheSubject() {
        assertThatThrownBy(() -> calculator.calculate(
                        "HK_1_2026_2027", List.of(subject("CNTT_4Y_V1", 1)), snapshot(false, List.of())))
                .isInstanceOfSatisfying(BaseException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(ErrorCode.PLANNING_DATA_UNAVAILABLE));
    }

    @Test
    void droppedAttemptIsNotMissingEnrollmentAndPassedSubjectDoesNotBecomeMandatoryImprovement() {
        var dropped = new Attempt(1, "CS101", 2026, 1, Outcome.DROPPED);
        assertThat(calculator.calculate(
                        "HK_2_2026_2027", List.of(subject("CNTT_4Y_V1", 1)), snapshot(true, List.of(dropped))))
                .isEmpty();
        var passed = new Attempt(1, "CS101", 2026, 1, Outcome.COMPLETED);
        var failedImprovement = new Attempt(1, "CS101", 2026, 2, Outcome.FAILED);
        assertThat(calculator.calculate(
                        "HK_2_2026_2027",
                        List.of(subject("CNTT_4Y_V1", 1)),
                        snapshot(true, List.of(passed, failedImprovement))))
                .isEmpty();
    }

    @Test
    void onTimeGroupTakesPrecedenceAndDuplicateProgramRowsDoNotDoubleCount() {
        var subject = subject("CNTT_4Y_V1", 1);
        var failure = new Attempt(1, "CS101", 2026, 1, Outcome.FAILED);
        var result =
                calculator.calculate("HK_1_2026_2027", List.of(subject, subject), snapshot(true, List.of(failure)));
        assertThat(result).singleElement().satisfies(demand -> {
            assertThat(demand.getOnTimeStudentCount()).isEqualTo(1);
            assertThat(demand.getRetakeStudentCount()).isZero();
            assertThat(demand.getExpectedStudentCount()).isEqualTo(1);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"HK_3_2026_2027", "HK_1_2026_2028", "HK_1_0000_0001", "invalid"})
    void rejectsUnsupportedSemesterCodes(String code) {
        assertThatThrownBy(() -> calculator.calculate(code, List.of(), snapshot(true, List.of())))
                .isInstanceOfSatisfying(BaseException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(ErrorCode.PLANNING_SEMESTER_UNSUPPORTED));
    }

    private PlanningSnapshot snapshot(boolean historyComplete, List<Attempt> attempts) {
        return new PlanningSnapshot(
                "TEST",
                historyComplete,
                List.of(new Student(1, "SV01", "CNTT_4Y_V1", 2026, ActivityStatus.STUDYING)),
                attempts);
    }

    private CurriculumOpeningSubjectResponse subject(String curriculumCode, int recommendedSemester) {
        return CurriculumOpeningSubjectResponse.builder()
                .curriculumCode(curriculumCode)
                .subjectId(1L)
                .subjectCode("CS101")
                .subjectName("Nhập môn lập trình")
                .credits(3)
                .recommendSemester(recommendedSemester)
                .build();
    }
}
