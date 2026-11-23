package com.eduhub.edu_hub.admission.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlanClass;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionCapacityPolicy;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionScoreBalancePolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionApplicantRepository;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanClassRepository;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.*;
import com.eduhub.edu_hub.people.domain.model.*;
import com.eduhub.edu_hub.people.domain.repository.*;
import com.eduhub.edu_hub.scheduling.application.facade.SchedulingFacade;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdmissionPlanQueryServiceTest {

    @Test
    void doesNotRecommendOldTargetsWhenSupplementExceedsAvailableClassIdentifiers() {
        var names = java.util.stream.IntStream.range(0, 991)
                .mapToObj(index -> "Thí sinh " + index)
                .toList();
        var plan = prepare(names);
        plan.setState(AdmissionPlanStatus.DRAFT);
        var population = applicants.findByPlan_IdAndDeletedFalseOrderByRowNumber(1L);
        population.getFirst().setState(AdmissionApplicantStatus.ENROLLED);
        population.stream().skip(1).forEach(applicant -> applicant.setAdministrativeClassId(null));
        planClasses.findByPlan_IdOrderByPosition(1L).getFirst().setTargetSize(1);

        var response = view.buildPlanResponse(plan);

        assertThat(response.canConfirm()).isFalse();
        assertThat(response.scenarios()).isEmpty();
        assertThat(response.warnings()).contains("Không đủ phạm vi 99 lớp để cấp định danh cho số sinh viên này");
    }

    @Test
    void describesFrozenEnrollmentSizesInsteadOfSuggestingAnEvenRedistribution() {
        var names = java.util.stream.IntStream.range(0, 11)
                .mapToObj(index -> "Thí sinh " + index)
                .toList();
        var plan = prepare(names);
        plan.setSelectedClassCount(2);
        var population = applicants.findByPlan_IdAndDeletedFalseOrderByRowNumber(1L);
        population.stream().limit(10).forEach(applicant -> applicant.setState(AdmissionApplicantStatus.ENROLLED));
        population.getLast().setAdministrativeClassId(11L);
        var firstClass = planClasses.findByPlan_IdOrderByPosition(1L).getFirst();
        firstClass.setTargetSize(10);
        when(planClasses.findByPlan_IdOrderByPosition(1L))
                .thenReturn(List.of(
                        firstClass,
                        AdmissionPlanClass.builder()
                                .administrativeClassId(11L)
                                .targetSize(1)
                                .build()));
        when(people.findClassesByIds(java.util.Set.of(10L, 11L)))
                .thenReturn(List.of(
                        AdmissionClassInfo.builder()
                                .id(10L)
                                .className("L01")
                                .currentSize(10)
                                .build(),
                        AdmissionClassInfo.builder()
                                .id(11L)
                                .className("L02")
                                .currentSize(0)
                                .build()));

        var response = view.buildPlanResponse(plan);

        assertThat(response.canConfirm()).isTrue();
        assertThat(response.scenarios()).singleElement().satisfies(scenario -> assertThat(scenario.targetSizes())
                .containsExactly(10, 1));
    }

    @Test
    void permitsConfirmingValidApplicantsWhileInvalidRowsRemainVisible() {
        var plan = prepare(List.of("Nguyễn Văn An"));
        var population = applicants.findByPlan_IdAndDeletedFalseOrderByRowNumber(1L);
        population.add(AdmissionApplicant.builder()
                .state(com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus.INVALID)
                .build());
        var response = view.buildPlanResponse(plan);
        assertThat(response.canConfirm()).isTrue();
        assertThat(response.confirmationBlockReasons()).isEmpty();
        assertThat(response.applicants()).hasSize(2);
        population.getFirst().setAdministrativeClassId(null);
        assertThat(view.buildPlanResponse(plan).canConfirm()).isFalse();
    }

    private final AdmissionApplicantRepository applicants = mock(AdmissionApplicantRepository.class);
    private final AdmissionPlanClassRepository planClasses = mock(AdmissionPlanClassRepository.class);
    private final PeopleFacade.Admission people = mock(PeopleFacade.Admission.class);
    private final SchedulingFacade.RoomCapacity rooms = mock(SchedulingFacade.RoomCapacity.class);
    private final AdmissionPlanQueryService view = new AdmissionPlanQueryService(
            applicants,
            planClasses,
            people,
            new AdmissionCapacityPolicy(),
            rooms,
            new AdmissionScoreBalancePolicy(),
            new com.eduhub.edu_hub.admission.api.mapper.AdmissionPlanResponseMapper(),
            org.mapstruct.factory.Mappers.getMapper(
                    com.eduhub.edu_hub.admission.api.mapper.AdmissionScenarioResponseMapper.class),
            com.eduhub.edu_hub.shared.systemconfig.SystemConfigTestData.configuredService());

    @ParameterizedTest
    @CsvSource({"1,0", "2,0", "3,0", "4,1", "5,1"})
    void onlyWarnsAboveThreeAndReportsTotalOncePerNormalizedName(int count, int expectedWarnings) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            names.add(i % 2 == 0 ? "Nguyễn Văn An" : "  NGUYỄN   VĂN AN ");
        }
        var response = view.buildPlanResponse(prepare(names));
        var warnings = response.warnings().stream()
                .filter(value -> value.contains("trùng họ tên"))
                .toList();
        assertThat(warnings).hasSize(expectedWarnings);
        if (expectedWarnings > 0) {
            assertThat(warnings.getFirst()).contains(count + " sinh viên trùng họ tên", "(tối đa 3)");
        }
    }

    @Test
    void appliesLimitSeparatelyToEachNameInTheSameClass() {
        var plan = prepare(List.of(
                "Nguyễn Văn An",
                "Nguyễn Văn An",
                "Nguyễn Văn An",
                "Trần Văn Bình",
                "Trần Văn Bình",
                "Trần Văn Bình",
                "Trần Văn Bình"));
        var warnings = view.buildPlanResponse(plan).warnings().stream()
                .filter(value -> value.contains("trùng họ tên"))
                .toList();
        assertThat(warnings).singleElement().satisfies(value -> assertThat(value)
                .contains("Trần Văn Bình", "4 sinh viên")
                .doesNotContain("Nguyễn Văn An"));
    }

    @Test
    void warnsAboutOtherMajorsSharingDerivedPrefixWithoutBlockingView() {
        var plan = prepare(List.of("Nguyễn Văn An"));
        when(people.findClassPrefixes())
                .thenReturn(List.of(
                        new AdmissionClassPrefix(2L, "CNTT", "Công nghệ thông tin", "CT"),
                        new AdmissionClassPrefix(4L, "DIENTU", "Cơ điện tử", "CT"),
                        new AdmissionClassPrefix(5L, "ATTT", "An toàn thông tin", "AT")));
        assertThat(view.buildPlanResponse(plan).warnings().stream()
                        .filter(value -> value.startsWith("Tiền tố lớp"))
                        .toList())
                .singleElement()
                .satisfies(value ->
                        assertThat(value).contains("CT", "Cơ điện tử", "DIENTU").doesNotContain("An toàn thông tin"));
    }

    private AdmissionPlan prepare(List<String> names) {
        AdmissionPlan plan = AdmissionPlan.builder().build();
        plan.setId(1L);
        plan.setMajorId(2L);
        plan.setCohortId(3L);
        plan.setTotalStudents(names.size());
        plan.setMinCapacity(1);
        plan.setMaxCapacity(10);
        plan.setSelectedClassCount(1);
        plan.setState(AdmissionPlanStatus.PREVIEWED);

        AdmissionPlanClass slot = AdmissionPlanClass.builder().build();
        slot.setAdministrativeClassId(10L);
        slot.setTargetSize(names.size());

        List<AdmissionApplicant> rows = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            AdmissionApplicant row = AdmissionApplicant.builder().build();
            row.setId((long) i + 1);
            row.setFullName(names.get(i));
            row.setEntranceScore(BigDecimal.valueOf(25));
            row.setAdministrativeClassId(10L);
            rows.add(row);
        }

        when(applicants.findByPlan_IdAndDeletedFalseOrderByRowNumber(1L)).thenReturn(rows);
        when(planClasses.findByPlan_IdOrderByPosition(1L)).thenReturn(List.of(slot));
        when(rooms.countSuitableRooms(10)).thenReturn(1L);
        when(people.findClassPrefixes())
                .thenReturn(List.of(new AdmissionClassPrefix(2L, "CNTT", "Công nghệ thông tin", "CT")));
        when(people.findClassesByIds(java.util.Set.of(10L)))
                .thenReturn(List.of(AdmissionClassInfo.builder()
                        .id(10L)
                        .className("L01")
                        .currentSize(0)
                        .build()));
        return plan;
    }
}
