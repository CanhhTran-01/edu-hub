package com.eduhub.edu_hub.scheduling.infrastructure.spreadsheet;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.enrollment.api.dto.response.ModuleClassStudentResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassRosterResponse;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassExportColumn;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ModuleClassSpreadsheetWriterTest {
    private final ModuleClassSpreadsheetWriter writer = new ModuleClassSpreadsheetWriter();
    private final ZonedDateTime exportedAt = ZonedDateTime.parse("2026-11-16T08:15:00+07:00[Asia/Ho_Chi_Minh]");
    private final ModuleClassResponse moduleClass = ModuleClassResponse.builder()
            .id(100L)
            .semesterId(7L)
            .semesterCode("HK_1_2026_2027")
            .moduleClassCode("MC_7_CS101_001")
            .subjectCode("CS101")
            .subjectName("Lập trình cơ bản")
            .lecturerName("Nguyễn Văn An")
            .build();

    @Test
    void writesOnlyApprovedColumnsAndPreservesIdentifiersStoredScoresAndBlanks() throws Exception {
        var student = new ModuleClassStudentResponse(
                10L, 1L, "0000123", "Nguyễn Thị Ánh", BigDecimal.ZERO, null, new BigDecimal("6.7"), "");
        byte[] spreadsheet = writer.writeStudents(
                new ModuleClassRosterResponse(moduleClass, List.of(student)),
                List.of(ModuleClassExportColumn.values()),
                exportedAt);

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(spreadsheet))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).contains("HK_1_2026_2027");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).contains("MC_7_CS101_001");
            assertThat(sheet.getRow(6).getCell(0).getStringCellValue()).contains("16/11/2026 08:15:00 +07:00");
            var header = sheet.getRow(8);
            assertThat(header.getLastCellNum()).isEqualTo((short) 6);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("MSSV");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Họ và tên");
            assertThat(header.getCell(5).getStringCellValue()).isEqualTo("Ghi chú");
            var studentRow = sheet.getRow(9);
            assertThat(studentRow.getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(studentRow.getCell(0).getStringCellValue()).isEqualTo("0000123");
            assertThat(studentRow.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(studentRow.getCell(2).getNumericCellValue()).isZero();
            assertThat(studentRow.getCell(3).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(studentRow.getCell(4).getNumericCellValue()).isEqualTo(6.7);
            assertThat(studentRow.getCell(5).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 9);
        }
    }

    @Test
    void honorsSelectedOrderAndKeepsFormulaLikeNamesAsLiteralText() throws Exception {
        var student = new ModuleClassStudentResponse(10L, 1L, "0000123", "=1+1", null, null, null, "");
        byte[] spreadsheet = writer.writeStudents(
                new ModuleClassRosterResponse(moduleClass, List.of(student)),
                List.of(ModuleClassExportColumn.FULL_NAME, ModuleClassExportColumn.STUDENT_CODE),
                exportedAt);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(spreadsheet))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(8).getLastCellNum()).isEqualTo((short) 2);
            assertThat(sheet.getRow(8).getCell(0).getStringCellValue()).isEqualTo("Họ và tên");
            assertThat(sheet.getRow(9).getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(9).getCell(0).getStringCellValue()).isEqualTo("=1+1");
            assertThat(sheet.getRow(9).getCell(1).getStringCellValue()).isEqualTo("0000123");
        }
    }

    @Test
    void emptyClassHasMetadataHeadersAndNoticeWithoutInventingStudentRows() throws Exception {
        byte[] spreadsheet = writer.writeStudents(
                new ModuleClassRosterResponse(moduleClass, List.of()),
                List.of(ModuleClassExportColumn.STUDENT_CODE, ModuleClassExportColumn.FULL_NAME),
                exportedAt);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(spreadsheet))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).isEqualTo("Số sinh viên: 0");
            assertThat(sheet.getRow(8).getCell(0).getStringCellValue()).isEqualTo("MSSV");
            assertThat((Object) sheet.getRow(9)).isNull();
            assertThat(sheet.getRow(10).getCell(0).getStringCellValue()).isEqualTo("Chưa có sinh viên đăng ký");
        }
    }

    @Test
    void writesStoredNotesAsLiteralText() throws Exception {
        var student = new ModuleClassStudentResponse(
                10L, 1L, "0000123", "Nguyễn Văn An", null, null, null, "=Ghi chú đã lưu");
        byte[] spreadsheet = writer.writeStudents(
                new ModuleClassRosterResponse(moduleClass, List.of(student)),
                List.of(
                        ModuleClassExportColumn.STUDENT_CODE,
                        ModuleClassExportColumn.FULL_NAME,
                        ModuleClassExportColumn.NOTE),
                exportedAt);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(spreadsheet))) {
            var noteCell = workbook.getSheetAt(0).getRow(9).getCell(2);
            assertThat(noteCell.getCellType()).isEqualTo(CellType.STRING);
            assertThat(noteCell.getStringCellValue()).isEqualTo("=Ghi chú đã lưu");
        }
    }
}
