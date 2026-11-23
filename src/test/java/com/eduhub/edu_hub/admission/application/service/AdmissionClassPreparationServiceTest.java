package com.eduhub.edu_hub.admission.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.model.*;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionCapacityPolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanClassRepository;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.AdmissionClassInfo;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;

class AdmissionClassPreparationServiceTest {
    private final AdmissionPlanClassRepository classRepository = mock(AdmissionPlanClassRepository.class);
    private final PeopleFacade.Admission people = mock(PeopleFacade.Admission.class);
    private final AdmissionClassPreparationService service =
            new AdmissionClassPreparationService(classRepository, people, new AdmissionCapacityPolicy());
    private final AdmissionPlan plan = AdmissionPlan.builder()
            .majorId(1L)
            .cohortId(8L)
            .minCapacity(40)
            .maxCapacity(50)
            .reserveCapacity(15)
            .build();
    private final List<AdmissionApplicant> applicants = new ArrayList<>();
    private final AdmissionPlanClass firstClass = plannedClass(10L, 1);
    private final AdmissionPlanClass secondClass = plannedClass(20L, 2);

    @BeforeEach
    void prepareExistingClasses() {
        plan.setId(7L);
        when(classRepository.findByPlan_IdOrderByPosition(7L)).thenReturn(List.of(firstClass, secondClass));
        when(people.lockClass(10L)).thenReturn(classInfo(10L, 1, 50));
        when(people.lockClass(20L)).thenReturn(classInfo(20L, 2, 50));
        when(people.findClasses(1L, 8L)).thenReturn(List.of(classInfo(10L, 1, 50), classInfo(20L, 2, 50)));
        IntStream.range(0, 50)
                .forEach(index -> applicants.add(AdmissionApplicant.builder()
                        .state(AdmissionApplicantStatus.ENROLLED)
                        .administrativeClassId(10L)
                        .build()));
        IntStream.range(0, 50)
                .forEach(index -> applicants.add(AdmissionApplicant.builder()
                        .state(AdmissionApplicantStatus.ENROLLED)
                        .administrativeClassId(20L)
                        .build()));
    }

    @Test
    void addsANormalClassWithoutReconfiguringOrMovingEnrolledStudents() {
        plan.setTotalStudents(140);
        when(people.createClass(any())).thenReturn(classInfo(30L, 3, 0));
        var prepared = service.prepareClassesAutomatically(plan, applicants);
        assertThat(prepared).extracting(AdmissionPlanClass::getTargetSize).containsExactly(50, 50, 40);
        assertThat(prepared.getFirst()).isSameAs(firstClass);
        assertThat(prepared.get(1)).isSameAs(secondClass);
        assertThat(prepared.getLast().getAdministrativeClassId()).isEqualTo(30L);
        assertThat(plan.getSelectedClassCount()).isEqualTo(3);
        verify(people, never()).configureClass(eq(10L), anyInt(), anyInt(), anyInt());
        verify(people, never()).configureClass(eq(20L), anyInt(), anyInt(), anyInt());
        verify(people).configureClass(30L, 40, 50, 15);
        assertThat(applicants.subList(0, 50)).allMatch(applicant -> applicant.getAdministrativeClassId() == 10L);
        verify(people).createClass(argThat(request -> request.sequence() == 3));
    }

    @Test
    void proposesReserveTargetsWhenSupplementCannotFormANormalClass() {
        plan.setTotalStudents(110);
        assertThat(service.prepareClassesAutomatically(plan, applicants))
                .extracting(AdmissionPlanClass::getTargetSize)
                .containsExactly(55, 55);
        verify(people, never()).createClass(any());
        verify(people, never()).configureClass(anyLong(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void rejectsClassPopulationChangedOutsideThePlan() {
        plan.setTotalStudents(110);
        when(people.lockClass(10L)).thenReturn(classInfo(10L, 1, 49));
        assertThatThrownBy(() -> service.prepareClassesAutomatically(plan, applicants))
                .isInstanceOfSatisfying(AdmissionPlanException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ADMISSION_DATA_CHANGED));
        verify(people, never()).createClass(any());
        verify(classRepository, never()).saveAll(anyList());
    }

    private AdmissionPlanClass plannedClass(Long classId, int position) {
        return AdmissionPlanClass.builder()
                .administrativeClassId(classId)
                .position(position)
                .targetSize(50)
                .build();
    }

    private AdmissionClassInfo classInfo(Long classId, int sequence, int currentSize) {
        return AdmissionClassInfo.builder()
                .id(classId)
                .classSequence(sequence)
                .majorId(1L)
                .cohortId(8L)
                .maxCapacity(50)
                .minCapacity(40)
                .reserveCapacity(15)
                .currentSize(currentSize)
                .lastStudentSequence(currentSize)
                .build();
    }
}
