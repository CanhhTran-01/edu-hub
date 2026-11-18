package com.eduhub.edu_hub.scheduling.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.scheduling.api.dto.request.ExportModuleClassStudentsRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassRosterResponse;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassRosterException;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassExportColumn;
import com.eduhub.edu_hub.scheduling.infrastructure.spreadsheet.ModuleClassSpreadsheetWriter;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.*;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.security.oauth2.jwt.Jwt;

class ModuleClassExportServiceTest {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-16T01:15:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final ModuleClassRosterService rosterService = mock(ModuleClassRosterService.class);
    private final ModuleClassSpreadsheetWriter writer = mock(ModuleClassSpreadsheetWriter.class);
    private final ModuleClassExportService service = new ModuleClassExportService(rosterService, writer, CLOCK);
    private final Jwt principal =
            Jwt.withTokenValue("token").header("alg", "HS256").claim("uid", 9L).build();
    private final ModuleClassRosterResponse roster = new ModuleClassRosterResponse(
            ModuleClassResponse.builder().id(100L).semesterId(7L).build(), List.of());

    @Test
    void rechecksRosterOnEveryExportAndPassesDefaultColumnsAndSharedClock() {
        when(rosterService.findRoster(principal, 100L)).thenReturn(roster);
        when(writer.writeStudents(roster, List.of(ModuleClassExportColumn.values()), ZonedDateTime.now(CLOCK)))
                .thenReturn(new byte[] {1, 2, 3});
        assertThat(service.exportStudents(principal, 100L, new ExportModuleClassStudentsRequest()))
                .containsExactly(1, 2, 3);
        assertThat(service.exportStudents(principal, 100L, new ExportModuleClassStudentsRequest()))
                .containsExactly(1, 2, 3);
        verify(rosterService, times(2)).findRoster(principal, 100L);
    }

    @Test
    void deniedClassNeverReachesSpreadsheetWriter() {
        when(rosterService.findRoster(principal, 100L))
                .thenThrow(new ModuleClassRosterException(ErrorCode.UNAUTHORIZED));
        assertThatThrownBy(() -> service.exportStudents(principal, 100L, new ExportModuleClassStudentsRequest()))
                .isInstanceOfSatisfying(
                        ModuleClassRosterException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verifyNoInteractions(writer);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsNullOrEmptyColumnSelection(List<ModuleClassExportColumn> columns) {
        assertInvalidColumns(columns);
    }

    @Test
    void rejectsMissingRequiredDuplicateOrNullColumns() {
        assertInvalidColumns(List.of(ModuleClassExportColumn.FULL_NAME));
        assertInvalidColumns(List.of(ModuleClassExportColumn.STUDENT_CODE));
        assertInvalidColumns(List.of(
                ModuleClassExportColumn.STUDENT_CODE,
                ModuleClassExportColumn.FULL_NAME,
                ModuleClassExportColumn.FULL_NAME));
        assertInvalidColumns(
                Arrays.asList(ModuleClassExportColumn.STUDENT_CODE, ModuleClassExportColumn.FULL_NAME, null));
    }

    @Test
    void unexpectedFileFailureIsReportedAsRetryableCreationError() {
        when(rosterService.findRoster(principal, 100L)).thenReturn(roster);
        when(writer.writeStudents(eq(roster), anyList(), any()))
                .thenThrow(new IllegalStateException("Chi tiết nội bộ"));
        assertThatThrownBy(() -> service.exportStudents(principal, 100L, new ExportModuleClassStudentsRequest()))
                .isInstanceOfSatisfying(ModuleClassRosterException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MODULE_CLASS_EXPORT_FAILED);
                    assertThat(exception.getMessage()).doesNotContain("Chi tiết nội bộ");
                });
    }

    private void assertInvalidColumns(List<ModuleClassExportColumn> columns) {
        when(rosterService.findRoster(principal, 100L)).thenReturn(roster);
        var request = new ExportModuleClassStudentsRequest();
        request.setColumns(columns);
        assertThatThrownBy(() -> service.exportStudents(principal, 100L, request))
                .isInstanceOfSatisfying(ModuleClassRosterException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getErrors()).singleElement().satisfies(fieldError -> assertThat(
                                    fieldError.getField())
                            .isEqualTo("columns"));
                });
        verifyNoInteractions(writer);
    }
}
