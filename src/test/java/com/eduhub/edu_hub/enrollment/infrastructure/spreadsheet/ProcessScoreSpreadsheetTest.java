package com.eduhub.edu_hub.enrollment.infrastructure.spreadsheet;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.enrollment.api.dto.response.ModuleClassStudentResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.ProcessScoreRosterResponse;
import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassGradingResponse;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ProcessScoreSpreadsheetTest {
    private final ProcessScoreSpreadsheet spreadsheet = new ProcessScoreSpreadsheet();
    private final ModuleClassGradingResponse moduleClass =
            new ModuleClassGradingResponse(100L, "MC_100", 1L, "HK_1_2026_2027", 70L, ModuleClassStatus.CLOSED);
    private final ProcessScoreRosterResponse roster = new ProcessScoreRosterResponse(
            moduleClass,
            List.of(new ModuleClassStudentResponse(10L, 1L, "00001", "=Nguyễn Văn Một", null, null, null, "=Ghi chú")));

    @Test
    void templatePreservesStudentCodesAndWritesUserTextWithoutFormulas() throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(spreadsheet.writeTemplate(roster)))) {
            var studentRow = workbook.getSheetAt(0).getRow(5);
            assertThat(studentRow.getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(studentRow.getCell(0).getStringCellValue()).isEqualTo("00001");
            assertThat(studentRow.getCell(1).getCellType()).isEqualTo(CellType.STRING);
            assertThat(studentRow.getCell(4).getCellType()).isEqualTo(CellType.STRING);
            assertThat(studentRow.getCell(2).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(workbook.getSheetAt(0).getRow(4).getCell(2).getStringCellValue())
                    .isEqualTo("TP1");
        }
    }

    @Test
    void readsNumericAndDecimalCommaScoresWithoutLosingLeadingZeros() throws Exception {
        var imported = spreadsheet.read(
                file(workbook -> {
                    var studentRow = workbook.getSheetAt(0).getRow(5);
                    studentRow.getCell(2).setCellValue(0);
                    studentRow.getCell(3).setCellValue("8,125");
                }),
                moduleClass);
        assertThat(imported.errors()).isEmpty();
        var student = imported.students().getFirst();
        assertThat(student.rowNumber()).isEqualTo(6);
        assertThat(student.studentCode()).isEqualTo("00001");
        assertThat(student.componentScore1()).isEqualByComparingTo("0");
        assertThat(student.componentScore2()).isEqualByComparingTo("8.125");
    }

    @Test
    void reportsBothMissingComponentsUsingPhysicalExcelRowNumbers() throws Exception {
        var imported = spreadsheet.read(
                file(workbook -> {
                    var sheet = workbook.getSheetAt(0);
                    var secondStudent = sheet.createRow(8);
                    secondStudent.createCell(0).setCellValue("00002");
                }),
                moduleClass);
        assertThat(imported.errors())
                .extracting(FieldErrorDetail::getField)
                .containsExactly(
                        "rows[6].componentScore1",
                        "rows[6].componentScore2",
                        "rows[9].componentScore1",
                        "rows[9].componentScore2");
    }

    @Test
    void rejectsNumericStudentCodesFormulaCellsAndExtraDataColumns() throws Exception {
        var imported = spreadsheet.read(
                file(workbook -> {
                    var studentRow = workbook.getSheetAt(0).getRow(5);
                    studentRow.getCell(0).setCellValue(1);
                    studentRow.getCell(1).setCellFormula("1+1");
                    studentRow.getCell(2).setCellValue(7);
                    studentRow.getCell(3).setCellFormula("8+1");
                    studentRow.createCell(5).setCellValue("Dữ liệu thừa");
                }),
                moduleClass);
        assertThat(imported.errors())
                .extracting(FieldErrorDetail::getField)
                .contains("rows[6].studentCode", "rows[6].columns", "rows[6].componentScore2");
    }

    @Test
    void rejectsFilesFromAnotherClassBeforeReadingStudentRows() throws Exception {
        var upload =
                file(workbook -> workbook.getSheetAt(0).getRow(1).getCell(1).setCellValue("OTHER_CLASS"));
        assertInvalidFile(() -> spreadsheet.read(upload, moduleClass));
    }

    @Test
    void rejectsMissingRequiredHeaderAndMultipleSheets() throws Exception {
        var missingHeader =
                file(workbook -> workbook.getSheetAt(0).getRow(4).getCell(3).setBlank());
        assertInvalidFile(() -> spreadsheet.read(missingHeader, moduleClass));
        var extraSheet = file(workbook -> workbook.createSheet("Khác"));
        assertInvalidFile(() -> spreadsheet.read(extraSheet, moduleClass));
    }

    @Test
    void rejectsCorruptWrongExtensionAndEmptyUploads() {
        assertInvalidFile(() -> spreadsheet.read(
                new MockMultipartFile("file", "scores.xlsx", null, new byte[] {1, 2, 3}), moduleClass));
        assertInvalidFile(
                () -> spreadsheet.read(new MockMultipartFile("file", "scores.xls", null, new byte[] {1}), moduleClass));
        assertInvalidFile(
                () -> spreadsheet.read(new MockMultipartFile("file", "scores.xlsx", null, new byte[0]), moduleClass));
    }

    @Test
    void rejectsTooManyPhysicalDataRowsEvenWhenTheLastRowsAreBlank() throws Exception {
        var upload = file(workbook -> workbook.getSheetAt(0).createRow(1005));
        assertInvalidFile(() -> spreadsheet.read(upload, moduleClass));
    }

    private MockMultipartFile file(Consumer<XSSFWorkbook> edit) throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(spreadsheet.writeTemplate(roster)));
                var output = new ByteArrayOutputStream()) {
            edit.accept(workbook);
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "scores.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        }
    }

    private void assertInvalidFile(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(EnrollmentException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.PROCESS_SCORE_IMPORT_INVALID));
    }
}
