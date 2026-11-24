package com.eduhub.edu_hub.people.application.facade.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.MajorResponse;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.application.facade.dto.AdmissionClassRequest;
import com.eduhub.edu_hub.people.application.service.StudentCodeGenerator;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.repository.*;
import com.eduhub.edu_hub.shared.systemconfig.SystemConfigTestData;
import com.eduhub.edu_hub.shared.systemconfig.application.exception.SystemConfigException;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PeopleAdmissionFacadeImplTest {
    private final AdministrativeClassRepository classes = mock(AdministrativeClassRepository.class);
    private final CohortRepository cohorts = mock(CohortRepository.class);
    private final CurriculumFacade.Majors majors = mock(CurriculumFacade.Majors.class);
    private final EntityManager entities = mock(EntityManager.class);

    @Test
    void createsClassUsingConfiguredPrefixInsteadOfInferringOneFromTheName() {
        var facade = facade("{\"CNTT\":\"IT\"}");
        var major = new MajorResponse(1L, "CNTT", "Công nghệ thông tin");
        when(majors.requireMajor(1L)).thenReturn(major);
        when(majors.findMajorsForClassNaming()).thenReturn(List.of(major));
        var cohort = new Cohort();
        cohort.setId(8L);
        cohort.setCodeSequence(8);
        cohort.setCohortCode("K8");
        when(cohorts.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(cohort));
        when(entities.getReference(Major.class, 1L))
                .thenReturn(Major.builder().majorName(major.majorName()).build());
        when(classes.saveAndFlush(any(AdministrativeClass.class))).thenAnswer(call -> {
            AdministrativeClass createdClass = call.getArgument(0);
            assertThat(createdClass.getClassName()).isEqualTo("IT8B");
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
        var major = new MajorResponse(1L, "CNTT", "Công nghệ thông tin");
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

    private PeopleAdmissionFacadeImpl facade(String mapping) {
        return new PeopleAdmissionFacadeImpl(
                mock(StudentRepository.class),
                classes,
                cohorts,
                majors,
                mock(StudentCodeGenerator.class),
                mock(IdentityFacade.class),
                entities,
                SystemConfigTestData.configuredService(Map.of(SystemConfigKey.MAJOR_CODE_PREFIXES, mapping)));
    }
}
