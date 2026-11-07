package com.eduhub.edu_hub.people.application.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentCodeGeneratorTest {
    @Mock
    StudentRepository students;

    @Mock
    CurriculumFacade.Majors majors;

    private StudentCodeGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new StudentCodeGenerator(students, majors);
    }

    @Test
    void generatesCodesWithCanonicalMajorAndPaddedComponents() {
        assertThat(generator.generate(administrativeClass(1L, "CT", "CT8", "L01"), 28))
                .isEqualTo("CNTT080128");
        assertThat(generator.generate(administrativeClass(2L, "AT", "AT20", "L02"), 5))
                .isEqualTo("ATTT200205");
        assertThat(generator.generate(administrativeClass(3L, "DT", "DT7", "L01"), 17))
                .isEqualTo("DTVT070117");
    }

    @Test
    void padsCohortAndSequenceWithZero() {
        assertThat(generator.generate(administrativeClass(1L, "dt", "DT7", "L01"), 1))
                .isEqualTo("DTVT070101");
    }

    @Test
    void usesCanonicalMajorCodeWithoutLocalPrefixMapping() {
        AdministrativeClass value = administrativeClass(4L, "AI", "AI8", "L01");
        assertThat(generator.generate(value, 1)).isEqualTo("TTNT080101");
    }

    @Test
    void continuesAfterHighestExistingCode() {
        AdministrativeClass value = administrativeClass(1L, "CT", "CT8", "L01");
        when(students.findStudentCodesByAdministrativeClassId(1L)).thenReturn(List.of("CT080101", "CT080128"));
        assertThat(generator.nextSequence(value)).isEqualTo(29);
    }

    @Test
    void maintainsIndependentSequencesForEachClass() {
        AdministrativeClass first = administrativeClass(1L, "CT", "CT8", "L01");
        AdministrativeClass second = administrativeClass(2L, "CT", "CT8", "L02");
        when(students.findStudentCodesByAdministrativeClassId(1L)).thenReturn(List.of("CT080128"));
        when(students.findStudentCodesByAdministrativeClassId(2L)).thenReturn(List.of("CT080205"));
        assertThat(generator.nextSequence(first)).isEqualTo(29);
        assertThat(generator.nextSequence(second)).isEqualTo(6);
    }

    @Test
    void rejectsMalformedClassCode() {
        AdministrativeClass value = administrativeClass(1L, "CT", "CT8", "CLASS-01");
        assertThatThrownBy(() -> generator.generate(value, 1)).isInstanceOf(BaseException.class);
    }

    @Test
    void rejectsSequenceAboveNinetyNine() {
        AdministrativeClass value = administrativeClass(1L, "CT", "CT8", "L01");
        assertThatThrownBy(() -> generator.generate(value, 100)).isInstanceOf(BaseException.class);
        when(students.findStudentCodesByAdministrativeClassId(1L)).thenReturn(List.of("CT080199"));
        assertThatThrownBy(() -> generator.nextSequence(value)).isInstanceOf(BaseException.class);
    }

    @Test
    void supportsConfiguredFormulaAndKeepsPersistedSequenceIndependentOfRosterOrder() {
        AdministrativeClass value = administrativeClass(1L, "CT", "CT8", "L01");
        org.springframework.test.util.ReflectionTestUtils.setField(
                generator, "format", "[MaNganh]-[SttKhoa]-[SttLop]-[SttSVTrongLop]");
        assertThat(generator.generate(value, 1)).isEqualTo("CNTT-08-01-01");
        value.setLastStudentSequence(28);
        assertThat(generator.nextSequence(value)).isEqualTo(29);
        assertThat(generator.nextSequence(value)).isEqualTo(30);
    }

    @Test
    void rejectsFormulaThatOmitsAnIdentityComponent() {
        AdministrativeClass value = administrativeClass(1L, "CT", "CT8", "L01");
        org.springframework.test.util.ReflectionTestUtils.setField(generator, "format", "[MaNganh][SttSVTrongLop]");
        assertThatThrownBy(() -> generator.generate(value, 1)).isInstanceOf(BaseException.class);
    }

    @Test
    void readsAlphabeticClassSuffixForStudentCodeWithoutUsingEntityId() {
        assertThat(generator.generate(administrativeClass(777L, "CT", "CT8", "CT8A"), 1))
                .isEqualTo("CNTT080101");
        assertThat(generator.generate(administrativeClass(999L, "CT", "CT8", "CT8AA"), 3))
                .isEqualTo("CNTT082703");
    }

    private AdministrativeClass administrativeClass(Long id, String majorCode, String cohortCode, String className) {
        Cohort cohort = new Cohort();
        cohort.setCohortCode(cohortCode);
        cohort.setCodeSequence(Integer.parseInt(cohortCode.replaceAll("[^0-9]", "")));
        AdministrativeClass value = new AdministrativeClass();
        value.setId(id);
        String canonical = java.util.Map.of("CT", "CNTT", "AT", "ATTT", "DT", "DTVT", "AI", "TTNT")
                .get(majorCode.toUpperCase(java.util.Locale.ROOT));
        Major major = Major.builder().majorCode(canonical).build();
        major.setId(id);
        value.setMajor(major);
        org.mockito.Mockito.lenient()
                .when(majors.requireMajor(id))
                .thenReturn(
                        new com.eduhub.edu_hub.curriculum.application.facade.dto.MajorResponse(id, canonical, "Major"));
        value.setCohort(cohort);
        value.setClassName(className);
        return value;
    }
}
