package com.eduhub.edu_hub.admission.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.admission.api.dto.request.CreateAdmissionPlanFromFileRequest;
import com.eduhub.edu_hub.admission.api.dto.request.ImportAdmissionApplicantsRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanResponse;
import com.eduhub.edu_hub.admission.application.exception.AdmissionImportScopeException;
import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionCapacityPolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanRepository;
import com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetTemplateWriter;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

/** Kiểm tra điều phối bằng mock; không xác nhận rollback transaction trên database thực tế. */
class AdmissionPlanningServiceTest {
    private final AdmissionPlanRepository planRepository = mock(AdmissionPlanRepository.class);
    private final PeopleFacade.Admission peopleAdmissionFacade = mock(PeopleFacade.Admission.class);
    private final CurriculumFacade.Majors majorCatalogFacade = mock(CurriculumFacade.Majors.class);
    private final AdmissionPlanQueryService planQueryService = mock(AdmissionPlanQueryService.class);
    private final AdmissionImportService importService = mock(AdmissionImportService.class);
    private final AdmissionPlanningService planningService = new AdmissionPlanningService(
            planRepository,
            peopleAdmissionFacade,
            majorCatalogFacade,
            com.eduhub.edu_hub.shared.systemconfig.SystemConfigTestData.configuredService(),
            new AdmissionCapacityPolicy(),
            planQueryService,
            importService,
            mock(AdmissionSpreadsheetTemplateWriter.class));
    private final MockMultipartFile file =
            new MockMultipartFile("file", "students.xlsx", "application/octet-stream", new byte[] {1});

    @BeforeEach
    void assignPlanIdWhenSaved() {
        when(planRepository.saveAndFlush(any(AdmissionPlan.class))).thenAnswer(invocation -> {
            AdmissionPlan plan = invocation.getArgument(0);
            plan.setId(7L);
            return plan;
        });
    }

    @Test
    void returnsTheImportedPopulationAndScenariosInsteadOfAnIntermediateEmptyPlan() {
        var importedPlan = AdmissionPlanResponse.builder()
                .id(7L)
                .revision(1)
                .totalStudents(83)
                .build();
        when(importService.importApplicants(7L, new ImportAdmissionApplicantsRequest(0, file)))
                .thenReturn(importedPlan);

        var response = planningService.createPlanFromFile(new CreateAdmissionPlanFromFileRequest(1L, 8L, file));

        assertThat(response).isSameAs(importedPlan);
        assertThat(response.totalStudents()).isEqualTo(83);
        ArgumentCaptor<AdmissionPlan> savedPlan = ArgumentCaptor.forClass(AdmissionPlan.class);
        verify(planRepository).saveAndFlush(savedPlan.capture());
        assertThat(savedPlan.getValue().getTotalStudents()).isZero();
        assertThat(savedPlan.getValue().getMinCapacity()).isEqualTo(40);
        assertThat(savedPlan.getValue().getMaxCapacity()).isEqualTo(50);
        assertThat(savedPlan.getValue().getReserveCapacity()).isEqualTo(15);
        verifyNoInteractions(planQueryService);
        var importOrder = inOrder(majorCatalogFacade, peopleAdmissionFacade, planRepository, importService);
        importOrder.verify(majorCatalogFacade).requireMajor(1L);
        importOrder.verify(peopleAdmissionFacade).requireCohort(8L);
        importOrder.verify(planRepository).saveAndFlush(any(AdmissionPlan.class));
        importOrder.verify(importService).importApplicants(7L, new ImportAdmissionApplicantsRequest(0, file));
    }

    @Test
    void rejectsCreatingAnotherPlanInTheSameScopeAfterTakingTheCohortLock() {
        when(planRepository.existsByMajorIdAndCohortIdAndDeletedFalse(1L, 8L)).thenReturn(true);
        assertThatThrownBy(
                        () -> planningService.createPlanFromFile(new CreateAdmissionPlanFromFileRequest(1L, 8L, file)))
                .isInstanceOfSatisfying(AdmissionPlanException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ADMISSION_PLAN_ALREADY_EXISTS));
        var lockOrder = inOrder(peopleAdmissionFacade, planRepository);
        lockOrder.verify(peopleAdmissionFacade).lockCohort(8L);
        lockOrder.verify(planRepository).existsByMajorIdAndCohortIdAndDeletedFalse(1L, 8L);
        verify(planRepository, never()).saveAndFlush(any());
        verifyNoInteractions(importService);
    }

    @Test
    void returnsAnEmptyLookupWithoutCreatingAPlan() {
        var response = planningService.findPlanByScope(1L, 8L);
        assertThat(response.plan()).isNull();
        assertThat(response.majorId()).isEqualTo(1L);
        verifyNoInteractions(importService, planQueryService);
        verify(planRepository, never()).saveAndFlush(any());
    }

    @Test
    void opensThePreviouslySavedPlanByScope() {
        var existingPlan = AdmissionPlan.builder().build();
        var response = AdmissionPlanResponse.builder().id(7L).build();
        when(planRepository.findByMajorIdAndCohortIdAndDeletedFalse(1L, 8L))
                .thenReturn(java.util.Optional.of(existingPlan));
        when(planQueryService.buildPlanResponse(existingPlan)).thenReturn(response);
        assertThat(planningService.findPlanByScope(1L, 8L).plan()).isSameAs(response);
        verifyNoInteractions(importService);
    }

    @Test
    void propagatesFileScopeErrorsWithoutReturningTheNewPlanAsASuccess() {
        var scopeError = new AdmissionImportScopeException(
                List.of(new FieldErrorDetail("majorCode", "Ngành trong file không khớp")));
        when(importService.importApplicants(7L, new ImportAdmissionApplicantsRequest(0, file)))
                .thenThrow(scopeError);
        assertThatThrownBy(
                        () -> planningService.createPlanFromFile(new CreateAdmissionPlanFromFileRequest(1L, 8L, file)))
                .isSameAs(scopeError);
        verifyNoInteractions(planQueryService);
    }
}
