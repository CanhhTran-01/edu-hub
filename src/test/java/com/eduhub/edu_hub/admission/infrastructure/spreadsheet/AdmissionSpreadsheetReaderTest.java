package com.eduhub.edu_hub.admission.infrastructure.spreadsheet;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.admission.application.exception.AdmissionImportScopeException;
import com.eduhub.edu_hub.admission.application.exception.AdmissionSpreadsheetException;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.infrastructure.config.AdmissionSettings;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.io.*;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class AdmissionSpreadsheetReaderTest {
    private static final AdmissionSpreadsheetScope SCOPE =
            new AdmissionSpreadsheetScope("CNTT", "Công nghệ thông tin", "CT8");
    private final AdmissionSpreadsheetReader parser = new AdmissionSpreadsheetReader(new AdmissionSettings());

    @Test
    void preservesTheOriginalCauseWhenTheUploadedFileCannotBeRead() throws Exception {
        IOException readFailure = new IOException("Không đọc được file tải lên");
        MultipartFile uploadedFile = mock(MultipartFile.class);
        when(uploadedFile.getOriginalFilename()).thenReturn("students.xlsx");
        when(uploadedFile.getInputStream()).thenThrow(readFailure);

        assertThatThrownBy(() -> parser.parse(uploadedFile, SCOPE))
                .isInstanceOfSatisfying(AdmissionSpreadsheetException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMISSION_IMPORT_INVALID);
                    assertThat(exception.getCause()).isSameAs(readFailure);
                });
    }

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
            sheet.getRow(AdmissionSpreadsheetReader.HEADER_ROW_INDEX + 2)
                    .getCell(3)
                    .setCellFormula("20+4");
            row(sheet, 3, "HS03", "012345678903", "Lê Văn Cường", "23");
            sheet.getRow(AdmissionSpreadsheetReader.HEADER_ROW_INDEX + 3)
                    .getCell(1)
                    .setCellValue(12345678903D);
            var parsed = parser.parse(file(workbook), SCOPE);
            assertThat(parsed)
                    .extracting(AdmissionSpreadsheetApplicant::getState)
                    .containsExactly(
                            AdmissionApplicantStatus.VALID,
                            AdmissionApplicantStatus.INVALID,
                            AdmissionApplicantStatus.INVALID);
        }
    }

    @Test
    void rejectsMissingRequiredHeaders() throws Exception {
        try (var workbook = workbook()) {
            var sheet = workbook.getSheetAt(0);
            sheet.removeRow(sheet.getRow(AdmissionSpreadsheetReader.HEADER_ROW_INDEX));
            sheet.createRow(AdmissionSpreadsheetReader.HEADER_ROW_INDEX)
                    .createCell(0)
                    .setCellValue("ho_ten");
            assertThatThrownBy(() -> parser.parse(file(workbook), SCOPE))
                    .isInstanceOfSatisfying(BaseException.class, error -> assertThat(error.getErrorCode())
                            .isEqualTo(ErrorCode.ADMISSION_IMPORT_INVALID));
        }
    }

    @Test
    void generatedTemplateCanBeReadBack() {
        assertThat(parser.parse(
                        new MockMultipartFile(
                                "file",
                                "template.xlsx",
                                "application/octet-stream",
                                new AdmissionSpreadsheetTemplateWriter().writeTemplate(SCOPE)),
                        SCOPE))
                .isEmpty();
    }

    @Test
    void generatedTemplateShowsSelectedScopeAndKeepsIdentifierColumnsAsText() throws Exception {
        try (var workbook = new XSSFWorkbook(
                new ByteArrayInputStream(new AdmissionSpreadsheetTemplateWriter().writeTemplate(SCOPE)))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Sinh viên trúng tuyển");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Sinh viên trúng tuyển");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Tên ngành");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Công nghệ thông tin");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Mã ngành");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("CNTT");
            assertThat(sheet.getRow(3).getPhysicalNumberOfCells()).isZero();
            assertThat(IntStream.range(0, 8)
                            .mapToObj(index -> sheet.getRow(4).getCell(index).getStringCellValue())
                            .toList())
                    .containsExactly(
                            "Mã hồ sơ",
                            "CCCD",
                            "Họ tên",
                            "Điểm đầu vào",
                            "Ngày sinh",
                            "Giới tính",
                            "Email",
                            "Số điện thoại");
            assertThat(sheet.getColumnStyle(1).getDataFormatString()).isEqualTo("@");
            assertThat(sheet.getColumnStyle(7).getDataFormatString()).isEqualTo("@");
            assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 5);
        }
    }

    @Test
    void importsApplicantsFromTheNewTemplateUsingTheSelectedCohort() throws Exception {
        try (var workbook = new XSSFWorkbook(
                new ByteArrayInputStream(new AdmissionSpreadsheetTemplateWriter().writeTemplate(SCOPE)))) {
            row(workbook.getSheetAt(0), 1, "HS01", "012345678901", "Nguyễn Văn An", "25");
            var selectedScope = new AdmissionSpreadsheetScope("CNTT", "Công nghệ thông tin", "CT9");
            assertThat(parser.parse(file(workbook), selectedScope))
                    .singleElement()
                    .satisfies(applicant -> {
                        assertThat(applicant.getState()).isEqualTo(AdmissionApplicantStatus.VALID);
                        assertThat(applicant.getRowNumber()).isEqualTo(6);
                        assertThat(applicant.getCitizenId()).isEqualTo("012345678901");
                    });
        }
    }

    @Test
    void rejectsTheNewTemplateWhenItsMajorDiffersFromTheSelectedMajor() throws Exception {
        try (var workbook = new XSSFWorkbook(
                new ByteArrayInputStream(new AdmissionSpreadsheetTemplateWriter().writeTemplate(SCOPE)))) {
            workbook.getSheetAt(0).getRow(2).getCell(1).setCellValue("ATTT");
            assertThatThrownBy(() -> parser.parse(file(workbook), SCOPE))
                    .isInstanceOfSatisfying(AdmissionImportScopeException.class, exception -> {
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMISSION_IMPORT_SCOPE_MISMATCH);
                        assertThat(exception.getErrors()).singleElement().satisfies(detail -> {
                            assertThat(detail.getField()).isEqualTo("majorCode");
                            assertThat(detail.getMessage()).contains("ATTT", "CNTT");
                        });
                    });
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void stillRequiresTheMajorLabelAndCodeInTheNewTemplate(int columnIndex) throws Exception {
        try (var workbook = new XSSFWorkbook(
                new ByteArrayInputStream(new AdmissionSpreadsheetTemplateWriter().writeTemplate(SCOPE)))) {
            workbook.getSheetAt(0).getRow(2).getCell(columnIndex).setBlank();
            assertThatThrownBy(() -> parser.parse(file(workbook), SCOPE))
                    .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.ADMISSION_IMPORT_METADATA_REQUIRED));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void rejectsFormulasAnywhereAboveTheNewTemplateHeader(int rowIndex) throws Exception {
        try (var workbook = new XSSFWorkbook(
                new ByteArrayInputStream(new AdmissionSpreadsheetTemplateWriter().writeTemplate(SCOPE)))) {
            workbook.getSheetAt(0).getRow(rowIndex).createCell(2).setCellFormula("1+1");
            assertThatThrownBy(() -> parser.parse(file(workbook), SCOPE))
                    .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.ADMISSION_IMPORT_INVALID));
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
            for (int i = 0; i < AdmissionSpreadsheetReader.HEADERS.size(); i++)
                header.createCell(i).setCellValue(AdmissionSpreadsheetReader.HEADERS.get(i));
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
                    .extracting(AdmissionSpreadsheetApplicant::getState)
                    .isEqualTo(AdmissionApplicantStatus.VALID);
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
                    .extracting(AdmissionSpreadsheetApplicant::getRowNumber)
                    .containsExactly(6, 8);
        }
    }

    @Test
    void importLimitCountsCandidateRowsWithoutChargingForTheMetadataAndHeader() throws Exception {
        var settings = new AdmissionSettings();
        settings.setMaxImportRows(2);
        var limitedParser = new AdmissionSpreadsheetReader(settings);
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

    @Test
    void readsReorderedVietnameseHeadersWithDifferentCaseSpacingAndUnicodeRepresentation() throws Exception {
        try (var workbook = workbook()) {
            var sheet = workbook.getSheetAt(0);
            var header = sheet.getRow(AdmissionSpreadsheetReader.HEADER_ROW_INDEX);
            for (int columnIndex = 0; columnIndex < 8; columnIndex++) {
                header.removeCell(header.getCell(columnIndex));
            }
            header.createCell(0).setCellValue(Normalizer.normalize("  ĐIỂM   ĐẦU VÀO ", Normalizer.Form.NFD));
            header.createCell(1).setCellValue("họ tên");
            header.createCell(2).setCellValue(" cccd ");
            header.createCell(3).setCellValue("mã hồ sơ");
            row(sheet, 1, "25,50", "  Nguyễn   Văn An ", "012345678901", " hs01 ");

            assertThat(parser.parse(file(workbook), SCOPE)).singleElement().satisfies(applicant -> {
                assertThat(applicant.getState()).isEqualTo(AdmissionApplicantStatus.VALID);
                assertThat(applicant.getApplicationCode()).isEqualTo("HS01");
                assertThat(applicant.getCitizenId()).isEqualTo("012345678901");
                assertThat(applicant.getFullName()).isEqualTo("Nguyễn Văn An");
                assertThat(applicant.getEntranceScore()).isEqualByComparingTo("25.50");
                assertThat(applicant.getDateOfBirth()).isNull();
                assertThat(applicant.getEmail()).isNull();
            });
        }
    }

    @Test
    void stillReadsFilesWithLegacyHeaders() throws Exception {
        try (var workbook = workbook()) {
            var sheet = workbook.getSheetAt(0);
            var legacyHeaders = List.of(
                    "ma_ho_so", "cccd", "ho_ten", "diem_dau_vao", "ngay_sinh", "gioi_tinh", "email", "so_dien_thoai");
            for (int index = 0; index < legacyHeaders.size(); index++) {
                sheet.getRow(AdmissionSpreadsheetReader.HEADER_ROW_INDEX)
                        .getCell(index)
                        .setCellValue(legacyHeaders.get(index));
            }
            row(
                    sheet,
                    1,
                    "HS01",
                    "012345678901",
                    "Nguyễn Văn An",
                    "25",
                    "03/02/2001",
                    "Nam",
                    " AN@Example.COM ",
                    "0123456789");

            assertThat(parser.parse(file(workbook), SCOPE)).singleElement().satisfies(applicant -> {
                assertThat(applicant.getState()).isEqualTo(AdmissionApplicantStatus.VALID);
                assertThat(applicant.getDateOfBirth()).isEqualTo(LocalDate.of(2001, 2, 3));
                assertThat(applicant.getGender()).isEqualTo("Nam");
                assertThat(applicant.getEmail()).isEqualTo("an@example.com");
                assertThat(applicant.getPhone()).isEqualTo("0123456789");
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mã hồ sơ", "  MÃ   HỒ SƠ  ", "ma_ho_so"})
    void rejectsDuplicateColumnsIncludingLegacyAliases(String duplicateHeader) throws Exception {
        try (var workbook = workbook()) {
            workbook.getSheetAt(0)
                    .getRow(AdmissionSpreadsheetReader.HEADER_ROW_INDEX)
                    .createCell(8)
                    .setCellValue(duplicateHeader);
            assertThatThrownBy(() -> parser.parse(file(workbook), SCOPE))
                    .isInstanceOfSatisfying(BaseException.class, error -> assertThat(error.getErrorCode())
                            .isEqualTo(ErrorCode.ADMISSION_IMPORT_INVALID));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2001-02-03", "03/02/2001"})
    void readsBothSupportedTextDateFormats(String dateOfBirth) throws Exception {
        try (var workbook = workbook()) {
            row(workbook.getSheetAt(0), 1, "HS01", "012345678901", "Nguyễn Văn An", "25", dateOfBirth);
            assertThat(parser.parse(file(workbook), SCOPE)).singleElement().satisfies(applicant -> {
                assertThat(applicant.getState()).isEqualTo(AdmissionApplicantStatus.VALID);
                assertThat(applicant.getDateOfBirth()).isEqualTo(LocalDate.of(2001, 2, 3));
            });
        }
    }

    @Test
    void readsExcelDateCells() throws Exception {
        try (var workbook = workbook()) {
            var sheet = workbook.getSheetAt(0);
            row(sheet, 1, "HS01", "012345678901", "Nguyễn Văn An", "25");
            var dateCell = sheet.getRow(AdmissionSpreadsheetReader.HEADER_ROW_INDEX + 1)
                    .createCell(4);
            dateCell.setCellValue(LocalDate.of(2001, 2, 3));
            var dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));
            dateCell.setCellStyle(dateStyle);

            assertThat(parser.parse(file(workbook), SCOPE)).singleElement().satisfies(applicant -> {
                assertThat(applicant.getState()).isEqualTo(AdmissionApplicantStatus.VALID);
                assertThat(applicant.getDateOfBirth()).isEqualTo(LocalDate.of(2001, 2, 3));
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"31/02/2001", "2001-02-30", "không phải ngày sinh"})
    void isolatesInvalidDatesAndPreservesLaterValidRows(String invalidDate) throws Exception {
        try (var workbook = workbook()) {
            var sheet = workbook.getSheetAt(0);
            row(sheet, 1, "HS01", "012345678901", "Nguyễn Văn An", "25", invalidDate);
            row(sheet, 2, "HS02", "012345678902", "Trần Văn Bình", "24", "03/02/2001");
            assertThat(parser.parse(file(workbook), SCOPE))
                    .extracting(AdmissionSpreadsheetApplicant::getState)
                    .containsExactly(AdmissionApplicantStatus.INVALID, AdmissionApplicantStatus.VALID);
        }
    }

    @Test
    void rejectsFormulaCellsEvenInUnmappedColumnsWithoutInvalidatingOtherRows() throws Exception {
        try (var workbook = workbook()) {
            var sheet = workbook.getSheetAt(0);
            row(sheet, 1, "HS01", "012345678901", "Nguyễn Văn An", "25");
            sheet.getRow(AdmissionSpreadsheetReader.HEADER_ROW_INDEX + 1)
                    .createCell(8)
                    .setCellFormula("1+1");
            row(sheet, 2, "HS02", "012345678902", "Trần Văn Bình", "24");
            assertThat(parser.parse(file(workbook), SCOPE))
                    .extracting(AdmissionSpreadsheetApplicant::getState)
                    .containsExactly(AdmissionApplicantStatus.INVALID, AdmissionApplicantStatus.VALID);
        }
    }

    @Test
    void rejectsWorkbooksWithMultipleSheets() throws Exception {
        try (var workbook = workbook()) {
            workbook.createSheet();
            assertThatThrownBy(() -> parser.parse(file(workbook), SCOPE))
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
        var header = sheet.createRow(AdmissionSpreadsheetReader.HEADER_ROW_INDEX);
        for (int i = 0; i < AdmissionSpreadsheetReader.HEADERS.size(); i++)
            header.createCell(i).setCellValue(AdmissionSpreadsheetReader.HEADERS.get(i));
    }

    private void row(org.apache.poi.ss.usermodel.Sheet sheet, int number, String... values) {
        var row = sheet.createRow(AdmissionSpreadsheetReader.HEADER_ROW_INDEX + number);
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
    }

    private MockMultipartFile file(XSSFWorkbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return new MockMultipartFile("file", "students.xlsx", "application/octet-stream", out.toByteArray());
    }
}
