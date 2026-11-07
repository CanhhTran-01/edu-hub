package com.eduhub.edu_hub.people.application.admission;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.people.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.io.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

class AdmissionSpreadsheetTest {
    private static final AdmissionSpreadsheet.Scope SCOPE =
            new AdmissionSpreadsheet.Scope("CNTT", "Công nghệ thông tin", "CT8");
    private final AdmissionSpreadsheet parser = new AdmissionSpreadsheet(new AdmissionSettings());

    @Test
    void acceptsNullableContactsAndKeepsLeadingZeroInCitizenId() throws Exception {
        try (var workbook = workbook()) {
            var sheet = workbook.getSheetAt(0);
            row(sheet, 1, "HS01", "012345678901", "Nguyễn Văn An", "25.50");
            var parsed = parser.parse(file(workbook), SCOPE);
            assertThat(parsed).singleElement().satisfies(value -> {
                assertThat(value.getCitizenId()).isEqualTo("012345678901");
                assertThat(value.getEmail()).isNull();
                assertThat(value.getPhone()).isNull();
                assertThat(value.getEntranceScore()).isEqualByComparingTo("25.50");
            });
        }
    }

    @Test
    void isolatesFormulaAndNumericCitizenIdErrorsWithoutDroppingValidRows() throws Exception {
        try (var workbook = workbook()) {
            var sheet = workbook.getSheetAt(0);
            row(sheet, 1, "HS01", "012345678901", "Nguyễn Văn An", "25.50");
            row(sheet, 2, "HS02", "012345678902", "Trần Văn Bình", "24");
            sheet.getRow(AdmissionSpreadsheet.HEADER_ROW_INDEX + 2).getCell(3).setCellFormula("20+4");
            row(sheet, 3, "HS03", "012345678903", "Lê Văn Cường", "23");
            sheet.getRow(AdmissionSpreadsheet.HEADER_ROW_INDEX + 3).getCell(1).setCellValue(12345678903D);
            var parsed = parser.parse(file(workbook), SCOPE);
            assertThat(parsed)
                    .extracting(AdmissionApplicant::getState)
                    .containsExactly(
                            AdmissionApplicant.State.VALID,
                            AdmissionApplicant.State.INVALID,
                            AdmissionApplicant.State.INVALID);
        }
    }

    @Test
    void rejectsMissingRequiredHeaders() throws Exception {
        try (var workbook = workbook()) {
            var sheet = workbook.getSheetAt(0);
            sheet.removeRow(sheet.getRow(AdmissionSpreadsheet.HEADER_ROW_INDEX));
            sheet.createRow(AdmissionSpreadsheet.HEADER_ROW_INDEX).createCell(0).setCellValue("ho_ten");
            assertThatThrownBy(() -> parser.parse(file(workbook), SCOPE))
                    .isInstanceOfSatisfying(BaseException.class, error -> assertThat(error.getErrorCode())
                            .isEqualTo(ErrorCode.ADMISSION_IMPORT_INVALID));
        }
    }

    @Test
    void generatedTemplateCanBeReadBack() {
        assertThat(parser.parse(
                        new MockMultipartFile(
                                "file", "template.xlsx", "application/octet-stream", parser.template(SCOPE)),
                        SCOPE))
                .isEmpty();
    }

    @Test
    void generatedTemplateShowsSelectedScopeAndKeepsIdentifierColumnsAsText() throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(parser.template(SCOPE)))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("ma_nganh");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("CNTT");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Công nghệ thông tin");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("ma_khoa");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("CT8");
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).isEqualTo("ma_ho_so");
            assertThat(sheet.getColumnStyle(1).getDataFormatString()).isEqualTo("@");
            assertThat(sheet.getColumnStyle(7).getDataFormatString()).isEqualTo("@");
            assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 5);
        }
    }

    @ParameterizedTest
    @CsvSource({"ATTT, CT8, majorCode", "CNTT, CT9, cohortCode"})
    void rejectsWrongMajorOrCohortWithTheFileAndExpectedCodes(String major, String cohort, String field)
            throws Exception {
        try (var workbook = workbook()) {
            workbook.getSheetAt(0).getRow(0).getCell(1).setCellValue(major);
            workbook.getSheetAt(0).getRow(2).getCell(1).setCellValue(cohort);
            assertThatThrownBy(() -> parser.parse(file(workbook), SCOPE))
                    .isInstanceOfSatisfying(AdmissionImportScopeException.class, error -> {
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ADMISSION_IMPORT_SCOPE_MISMATCH);
                        assertThat(error.getErrors()).singleElement().satisfies(detail -> {
                            assertThat(detail.getField()).isEqualTo(field);
                            assertThat(detail.getMessage())
                                    .contains(
                                            field.equals("majorCode") ? major : cohort,
                                            field.equals("majorCode") ? SCOPE.majorCode() : SCOPE.cohortCode());
                        });
                    });
        }
    }

    @Test
    void reportsBothMismatchesTogether() throws Exception {
        try (var workbook = workbook()) {
            workbook.getSheetAt(0).getRow(0).getCell(1).setCellValue("ATTT");
            workbook.getSheetAt(0).getRow(2).getCell(1).setCellValue("CT9");
            assertThatThrownBy(() -> parser.parse(file(workbook), SCOPE))
                    .isInstanceOfSatisfying(AdmissionImportScopeException.class, error -> assertThat(error.getErrors())
                            .extracting(FieldErrorDetail::getField)
                            .containsExactly("majorCode", "cohortCode"));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void rejectsMissingScopeCodesInsteadOfUsingTheSelectedScopeImplicitly(int metadataRow) throws Exception {
        try (var workbook = workbook()) {
            workbook.getSheetAt(0).getRow(metadataRow).getCell(1).setBlank();
            assertThatThrownBy(() -> parser.parse(file(workbook), SCOPE))
                    .isInstanceOfSatisfying(BaseException.class, error -> assertThat(error.getErrorCode())
                            .isEqualTo(ErrorCode.ADMISSION_IMPORT_METADATA_REQUIRED));
        }
    }

    @Test
    void rejectsLegacyTemplateWithoutScopeMetadata() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var header = workbook.createSheet().createRow(0);
            for (int i = 0; i < AdmissionSpreadsheet.HEADERS.size(); i++)
                header.createCell(i).setCellValue(AdmissionSpreadsheet.HEADERS.get(i));
            assertThatThrownBy(() -> parser.parse(file(workbook), SCOPE))
                    .isInstanceOfSatisfying(BaseException.class, error -> assertThat(error.getErrorCode())
                            .isEqualTo(ErrorCode.ADMISSION_IMPORT_METADATA_REQUIRED));
        }
    }

    @Test
    void comparesCodesWithoutDependingOnTheDisplayNameOrLetterCase() throws Exception {
        try (var workbook = workbook()) {
            var sheet = workbook.getSheetAt(0);
            sheet.getRow(0).getCell(1).setCellValue(" cntt ");
            sheet.getRow(1).getCell(1).setCellValue("Tên ngành cũ trước khi đổi tên danh mục");
            sheet.getRow(2).getCell(1).setCellValue(" ct8 ");
            row(sheet, 1, "HS01", "012345678901", "Nguyễn Văn An", "25");
            assertThat(parser.parse(file(workbook), SCOPE))
                    .singleElement()
                    .extracting(AdmissionApplicant::getState)
                    .isEqualTo(AdmissionApplicant.State.VALID);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 4})
    void rejectsFormulasInSharedInformationAndHeadersForTheWholeFile(int row) throws Exception {
        try (var workbook = workbook()) {
            workbook.getSheetAt(0).getRow(row).getCell(1).setCellFormula("1+1");
            assertThatThrownBy(() -> parser.parse(file(workbook), SCOPE))
                    .isInstanceOfSatisfying(BaseException.class, error -> assertThat(error.getErrorCode())
                            .isEqualTo(ErrorCode.ADMISSION_IMPORT_INVALID));
        }
    }

    @Test
    void preservesActualExcelRowNumbersAcrossBlankRows() throws Exception {
        try (var workbook = workbook()) {
            row(workbook.getSheetAt(0), 1, "HS01", "012345678901", "Nguyễn Văn An", "25");
            row(workbook.getSheetAt(0), 3, "HS02", "012345678902", "Trần Văn Bình", "24");
            assertThat(parser.parse(file(workbook), SCOPE))
                    .extracting(AdmissionApplicant::getRowNumber)
                    .containsExactly(6, 8);
        }
    }

    @Test
    void importLimitCountsCandidateRowsWithoutChargingForTheMetadataAndHeader() throws Exception {
        var settings = new AdmissionSettings();
        settings.setMaxImportRows(2);
        var limitedParser = new AdmissionSpreadsheet(settings);
        try (var workbook = workbook()) {
            var sheet = workbook.getSheetAt(0);
            row(sheet, 1, "HS01", "012345678901", "Nguyễn Văn An", "25");
            row(sheet, 2, "HS02", "012345678902", "Trần Văn Bình", "24");
            assertThat(limitedParser.parse(file(workbook), SCOPE)).hasSize(2);
            row(sheet, 3, "HS03", "012345678903", "Lê Văn Cường", "23");
            assertThatThrownBy(() -> limitedParser.parse(file(workbook), SCOPE))
                    .isInstanceOfSatisfying(BaseException.class, error -> assertThat(error.getErrorCode())
                            .isEqualTo(ErrorCode.ADMISSION_IMPORT_INVALID));
        }
    }

    private XSSFWorkbook workbook() {
        var workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet();
        var major = sheet.createRow(0);
        major.createCell(0).setCellValue("ma_nganh");
        major.createCell(1).setCellValue("CNTT");
        var name = sheet.createRow(1);
        name.createCell(0).setCellValue("ten_nganh");
        name.createCell(1).setCellValue("Công nghệ thông tin");
        var cohort = sheet.createRow(2);
        cohort.createCell(0).setCellValue("ma_khoa");
        cohort.createCell(1).setCellValue("CT8");
        header(sheet);
        return workbook;
    }

    private void header(org.apache.poi.ss.usermodel.Sheet sheet) {
        var header = sheet.createRow(AdmissionSpreadsheet.HEADER_ROW_INDEX);
        for (int i = 0; i < AdmissionSpreadsheet.HEADERS.size(); i++)
            header.createCell(i).setCellValue(AdmissionSpreadsheet.HEADERS.get(i));
    }

    private void row(org.apache.poi.ss.usermodel.Sheet sheet, int number, String... values) {
        var row = sheet.createRow(AdmissionSpreadsheet.HEADER_ROW_INDEX + number);
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
    }

    private MockMultipartFile file(XSSFWorkbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return new MockMultipartFile("file", "students.xlsx", "application/octet-stream", out.toByteArray());
    }
}
