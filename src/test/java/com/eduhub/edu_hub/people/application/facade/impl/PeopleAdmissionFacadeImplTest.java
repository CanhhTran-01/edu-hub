package com.eduhub.edu_hub.people.application.facade.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.api.dto.response.MajorReferenceResponse;
import com.eduhub.edu_hub.curriculum.api.dto.response.MajorSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.application.facade.dto.AdmissionClassRequest;
import com.eduhub.edu_hub.people.application.service.StudentCodeGenerator;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.repository.*;
import com.eduhub.edu_hub.shared.systemconfig.SystemConfigTestData;
import com.eduhub.edu_hub.shared.systemconfig.application.exception.SystemConfigException;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PeopleAdmissionFacadeImplTest {
    private final AdministrativeClassRepository classes = mock(AdministrativeClassRepository.class);
    private final CohortRepository cohorts = mock(CohortRepository.class);
    private final CurriculumFacade.Majors majors = mock(CurriculumFacade.Majors.class);
    private final CurriculumFacade.ReferenceData curriculumReferenceData = mock(CurriculumFacade.ReferenceData.class);

    @Test
    void createsClassUsingConfiguredPrefixInsteadOfInferringOneFromTheName() {
        var facade = facade("{\"CNTT\":\"IT\"}");
        var major = new MajorReferenceResponse(1L, "CNTT", "Công nghệ thông tin");
        when(majors.requireMajor(1L)).thenReturn(major);
        when(majors.findMajorsForClassNaming()).thenReturn(List.of(major));
        var cohort = new Cohort();
        cohort.setId(8L);
        cohort.setCodeSequence(8);
        cohort.setCohortCode("K8");
        when(cohorts.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(cohort));
        when(classes.saveAndFlush(any(AdministrativeClass.class))).thenAnswer(call -> {
            AdministrativeClass createdClass = call.getArgument(0);
            assertThat(createdClass.getClassName()).isEqualTo("IT8B");
            assertThat(createdClass.getMajorId()).isEqualTo(1L);
            createdClass.setId(10L);
            return createdClass;
        });
        var created = facade.createClass(AdmissionClassRequest.builder()
                .majorId(1L)
                .cohortId(8L)
                .sequence(2)
                .minimum(40)
                .maximum(50)
                .reserve(15)
                .build());
        assertThat(created.getClassName()).isEqualTo("IT8B");
        assertThat(created.getClassSequence()).isEqualTo(2);
    }

    @Test
    void missingMappingIsVisibleWhenReadingPrefixesButPreventsCreatingANewClass() {
        var facade = facade("{}");
        var major = new MajorReferenceResponse(1L, "CNTT", "Công nghệ thông tin");
        when(majors.findMajorsForClassNaming()).thenReturn(List.of(major));
        assertThat(facade.findClassPrefixes()).singleElement().satisfies(prefix -> {
            assertThat(prefix.majorName()).isEqualTo("Công nghệ thông tin");
            assertThat(prefix.prefix()).isNull();
        });
        when(majors.requireMajor(1L)).thenReturn(major);
        var cohort = new Cohort();
        cohort.setCodeSequence(8);
        when(cohorts.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(cohort));
        assertThatThrownBy(() -> facade.createClass(AdmissionClassRequest.builder()
                        .majorId(1L)
                        .cohortId(8L)
                        .sequence(1)
                        .build()))
                .isInstanceOf(SystemConfigException.class);
        verifyNoInteractions(classes);
    }

    @Test
    void classListLoadsHistoricalMajorNamesOnceForTheEntireBatch() {
        var facade = facade("{}");
        var cohort = new Cohort();
        cohort.setId(8L);
        cohort.setCohortCode("K8");
        cohort.setCodeSequence(8);
        var firstClass = AdministrativeClass.builder()
                .majorId(1L)
                .cohort(cohort)
                .className("CT8A")
                .build();
        firstClass.setId(10L);
        var secondClass = AdministrativeClass.builder()
                .majorId(1L)
                .cohort(cohort)
                .className("CT8B")
                .build();
        secondClass.setId(11L);
        when(classes.findAllById(Set.of(10L, 11L))).thenReturn(List.of(firstClass, secondClass));
        when(curriculumReferenceData.findMajorsByIds(Set.of(1L)))
                .thenReturn(List.of(new MajorSimpleResponse(1L, "OLD", "Ngành đã ẩn", null, true)));

        assertThat(facade.findClassesByIds(Set.of(10L, 11L)))
                .extracting(classInfo -> classInfo.getMajorName())
                .containsExactly("Ngành đã ẩn", "Ngành đã ẩn");
        verify(curriculumReferenceData).findMajorsByIds(Set.of(1L));
        verifyNoMoreInteractions(curriculumReferenceData);
    }

    private PeopleAdmissionFacadeImpl facade(String mapping) {
        return new PeopleAdmissionFacadeImpl(
                mock(StudentRepository.class),
                classes,
                cohorts,
                majors,
                mock(StudentCodeGenerator.class),
                mock(IdentityFacade.class),
                curriculumReferenceData,
                SystemConfigTestData.configuredService(Map.of(SystemConfigKey.MAJOR_CODE_PREFIXES, mapping)));
    }
}
