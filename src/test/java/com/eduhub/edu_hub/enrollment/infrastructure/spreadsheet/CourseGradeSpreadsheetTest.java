package com.eduhub.edu_hub.enrollment.infrastructure.spreadsheet;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.enrollment.api.dto.response.CourseGradeResponse;
import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassGradingResponse;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import java.io.*;
import java.util.List;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class CourseGradeSpreadsheetTest {
    private final CourseGradeSpreadsheet spreadsheet = new CourseGradeSpreadsheet();
    private final ModuleClassGradingResponse moduleClass =
            new ModuleClassGradingResponse(100L, "MC_100", 7L, "HK_1_2026_2027", null, ModuleClassStatus.COMPLETED);

    @Test
    void writesExactlyThreeColumnsAndPreservesCodesAndUntrustedNamesAsText() throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(4).getLastCellNum()).isEqualTo((short) 3);
            assertThat(sheet.getRow(5).getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).isEqualTo("00001");
            assertThat(sheet.getRow(5).getCell(1).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(5).getCell(2).getCellType()).isEqualTo(CellType.BLANK);
        }
    }

    @Test
    void readsReorderedRowsAndDecimalCommasWithoutUsingTheNameAsIdentity() throws Exception {
        var imported = spreadsheet.read(
                file(workbook -> {
                    var sheet = workbook.getSheetAt(0);
                    sheet.getRow(5).getCell(0).setCellValue("00002");
                    sheet.getRow(5).getCell(1).setCellValue("Tên chỉ hiển thị");
                    sheet.getRow(5).getCell(2).setCellValue("8,125");
                    var nextStudent = sheet.createRow(8);
                    nextStudent.createCell(0).setCellValue("00001");
                    nextStudent.createCell(2).setCellValue(0);
                }),
                moduleClass);
        assertThat(imported.errors()).isEmpty();
        assertThat(imported.students())
                .extracting(student -> student.studentCode())
                .containsExactly("00002", "00001");
        assertThat(imported.students().getFirst().finalExamScore()).isEqualByComparingTo("8.125");
        assertThat(imported.students().get(1).finalExamScore()).isZero();
        assertThat(imported.students().get(1).rowNumber()).isEqualTo(9);
    }

    @Test
    void reportsBlankScoresAndNumericStudentCodesWithoutCoercion() throws Exception {
        var imported = spreadsheet.read(
                file(workbook -> workbook.getSheetAt(0).getRow(5).getCell(0).setCellValue(1)), moduleClass);
        assertThat(imported.errors())
                .extracting(FieldErrorDetail::getField)
                .contains("rows[6].studentCode", "rows[6].finalExamScore");
        assertThat(imported.students().getFirst().finalExamScore()).isNull();
    }

    @Test
    void rejectsFormulaCellsExtraColumnsAndWrongClassMetadata() throws Exception {
        var imported = spreadsheet.read(
                file(workbook -> {
                    var student = workbook.getSheetAt(0).getRow(5);
                    student.getCell(1).setCellFormula("1+1");
                    student.getCell(2).setCellFormula("8+1");
                    student.createCell(3).setCellValue("Thừa cột");
                }),
                moduleClass);
        assertThat(imported.errors())
                .extracting(FieldErrorDetail::getField)
                .contains("rows[6].columns", "rows[6].finalExamScore");
        var wrongClass =
                file(workbook -> workbook.getSheetAt(0).getRow(1).getCell(1).setCellValue("OTHER"));
        assertThatThrownBy(() -> spreadsheet.read(wrongClass, moduleClass)).isInstanceOf(EnrollmentException.class);
    }

    @Test
    void rejectsMultipleSheetsAndMoreThanOneThousandPhysicalRows() throws Exception {
        var extraSheet = file(workbook -> workbook.createSheet("Khác"));
        assertThatThrownBy(() -> spreadsheet.read(extraSheet, moduleClass)).isInstanceOf(EnrollmentException.class);
        var tooManyRows = file(
                workbook -> workbook.getSheetAt(0).createRow(1005).createCell(0).setCellValue("00002"));
        assertThatThrownBy(() -> spreadsheet.read(tooManyRows, moduleClass)).isInstanceOf(EnrollmentException.class);
    }

    private byte[] template() {
        var student = mock(CourseGradeResponse.class);
        when(student.studentCode()).thenReturn("00001");
        when(student.fullName()).thenReturn("=Tên sinh viên");
        return spreadsheet.writeTemplate(moduleClass, List.of(student));
    }

    @Test
    void doesNotProduceATemplateThatExceedsTheImportLimit() {
        assertThatThrownBy(() -> spreadsheet.writeTemplate(
                        moduleClass, java.util.Collections.nCopies(1001, mock(CourseGradeResponse.class))))
                .isInstanceOf(EnrollmentException.class);
    }

    private MockMultipartFile file(Consumer<XSSFWorkbook> change) throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(template()));
                var output = new ByteArrayOutputStream()) {
            change.accept(workbook);
            workbook.write(output);
            return new MockMultipartFile("file", "course-grades.xlsx", null, output.toByteArray());
        }
    }
}
