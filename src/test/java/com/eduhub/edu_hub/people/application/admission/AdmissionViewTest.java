package com.eduhub.edu_hub.people.application.admission;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.people.domain.model.*;
import com.eduhub.edu_hub.people.domain.repository.*;
import com.eduhub.edu_hub.scheduling.application.SchedulingFacade;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdmissionViewTest {
    private final AdmissionApplicantRepository applicants = mock(AdmissionApplicantRepository.class);
    private final AdmissionPlanClassRepository planClasses = mock(AdmissionPlanClassRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final SchedulingFacade.RoomCapacity rooms = mock(SchedulingFacade.RoomCapacity.class);
    private final com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade.Majors majors =
            mock(com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade.Majors.class);
    private final AdmissionView view =
            new AdmissionView(applicants, planClasses, students, new AdmissionCapacityPolicy(), rooms, majors);

    @ParameterizedTest
    @CsvSource({"1,0", "2,0", "3,0", "4,1", "5,1"})
    void onlyWarnsAboveThreeAndReportsTotalOncePerNormalizedName(int count, int expectedWarnings) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            names.add(i % 2 == 0 ? "Nguyễn Văn An" : "  NGUYỄN   VĂN AN ");
        }
        var response = view.response(prepare(names));
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
        var warnings = view.response(plan).warnings().stream()
                .filter(value -> value.contains("trùng họ tên"))
                .toList();
        assertThat(warnings).singleElement().satisfies(value -> assertThat(value)
                .contains("Trần Văn Bình", "4 sinh viên")
                .doesNotContain("Nguyễn Văn An"));
    }

    @Test
    void warnsAboutOtherMajorsSharingDerivedPrefixWithoutBlockingView() {
        var plan = prepare(List.of("Nguyễn Văn An"));
        when(majors.findMajorsForClassNaming())
                .thenReturn(List.of(
                        new com.eduhub.edu_hub.curriculum.application.facade.dto.MajorResponse(
                                2L, "CNTT", "Công nghệ thông tin"),
                        new com.eduhub.edu_hub.curriculum.application.facade.dto.MajorResponse(
                                4L, "DIENTU", "Cơ điện tử"),
                        new com.eduhub.edu_hub.curriculum.application.facade.dto.MajorResponse(
                                5L, "ATTT", "An toàn thông tin")));
        assertThat(view.response(plan).warnings().stream()
                        .filter(value -> value.startsWith("Tiền tố lớp"))
                        .toList())
                .singleElement()
                .satisfies(value ->
                        assertThat(value).contains("CT", "Cơ điện tử", "DIENTU").doesNotContain("An toàn thông tin"));
    }

    private AdmissionPlan prepare(List<String> names) {
        Major major = Major.builder().majorName("Công nghệ thông tin").build();
        major.setId(2L);
        Cohort cohort = new Cohort();
        cohort.setId(3L);
        AdmissionPlan plan = new AdmissionPlan();
        plan.setId(1L);
        plan.setMajor(major);
        plan.setCohort(cohort);
        plan.setTotalStudents(names.size());
        plan.setMinCapacity(1);
        plan.setMaxCapacity(10);
        plan.setSelectedClassCount(1);
        plan.setState(AdmissionPlan.State.PREVIEWED);
        AdministrativeClass target = new AdministrativeClass();
        target.setId(10L);
        target.setClassName("L01");
        AdmissionPlanClass slot = new AdmissionPlanClass();
        slot.setAdministrativeClass(target);
        slot.setTargetSize(names.size());
        List<AdmissionApplicant> rows = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            AdmissionApplicant row = new AdmissionApplicant();
            row.setId((long) i + 1);
            row.setFullName(names.get(i));
            row.setEntranceScore(BigDecimal.valueOf(25));
            row.setAdministrativeClass(target);
            rows.add(row);
        }
        when(applicants.findByPlan_IdOrderByRowNumber(1L)).thenReturn(rows);
        when(planClasses.findByPlan_IdOrderByPosition(1L)).thenReturn(List.of(slot));
        when(rooms.countSuitableRooms(10)).thenReturn(1L);
        return plan;
    }
}
