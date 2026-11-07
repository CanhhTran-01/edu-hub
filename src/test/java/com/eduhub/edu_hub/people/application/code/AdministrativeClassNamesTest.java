package com.eduhub.edu_hub.people.application.code;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.shared.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AdministrativeClassNamesTest {
    @ParameterizedTest
    @CsvSource({
        "Công nghệ thông tin,CT",
        "Điện tử viễn thông,DT",
        "An toàn thông tin,AT",
        "Trí tuệ nhân tạo,TT",
        "Luật,L"
    })
    void derivesPrefixFromFirstAndLastInitialWithoutDiacritics(String name, String prefix) {
        assertThat(AdministrativeClassNames.prefix("  " + name.toLowerCase(java.util.Locale.ROOT) + "  "))
                .isEqualTo(prefix);
    }

    @ParameterizedTest
    @CsvSource({"1,A", "2,B", "26,Z", "27,AA", "28,AB", "52,AZ", "53,BA", "99,CU"})
    void createsNamesAndReadsBackTheirBusinessSequence(int sequence, String suffix) {
        String name = AdministrativeClassNames.create("Công nghệ thông tin", 8, sequence);
        assertThat(name).isEqualTo("CT8" + suffix);
        assertThat(AdministrativeClassNames.sequence(name, 8)).hasValue(sequence);
    }

    @Test
    void preservesLegacyNamesAndDoesNotUseDatabaseId() {
        assertThat(AdministrativeClassNames.sequence("L01", 8)).hasValue(1);
        assertThat(AdministrativeClassNames.sequence("l99", 8)).hasValue(99);
        assertThat(AdministrativeClassNames.sequence("XY8AA", 8)).hasValue(27);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CT9A", "CT08A", "CT8CV", "CT8AAA", "L00", "CLASS-01", "CT8", ""})
    void rejectsWrongCohortOrOutOfRangeSequence(String name) {
        assertThat(AdministrativeClassNames.sequence(name, 8)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100})
    void cannotGenerateSequenceOutsideStudentCodeRange(int sequence) {
        assertThatThrownBy(() -> AdministrativeClassNames.create("Công nghệ thông tin", 8, sequence))
                .isInstanceOf(BaseException.class);
    }
}
