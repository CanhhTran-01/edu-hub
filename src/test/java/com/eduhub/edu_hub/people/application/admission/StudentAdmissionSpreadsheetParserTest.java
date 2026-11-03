package com.eduhub.edu_hub.people.application.admission;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class StudentAdmissionSpreadsheetParserTest {
    private final StudentAdmissionSpreadsheetParser parser = new StudentAdmissionSpreadsheetParser();

    @Test
    void readsValidFileAndKeepsInvalidRowsSeparate() throws Exception {
        byte[] file;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            var header = sheet.createRow(0);
            for (int i = 0; i < StudentAdmissionSpreadsheetParser.HEADERS.size(); i++) {
                header.createCell(i).setCellValue(StudentAdmissionSpreadsheetParser.HEADERS.get(i));
            }
            row(sheet.createRow(1), "HS01", "Nguyễn An", "01/01/2007", "NAM", "an@example.test", "25.5", "ATTT", "K20", "CHINH_QUY");
            row(sheet.createRow(2), "HS02", "Trần Bình", "bad-date", "NAM", "binh@example.test", "24", "ATTT", "K20", "CHINH_QUY");
            workbook.write(output); file = output.toByteArray();
        }
        AdmissionParseResult result = parser.parse(new ByteArrayInputStream(file));
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.candidates()).extracting(AdmissionCandidate::getApplicationCode).containsExactly("HS01");
        assertThat(result.errors()).anyMatch(error -> error.rowNumber() == 3 && error.field().equals("ngay_sinh"));
    }

    @Test
    void reportsMissingHeaders() throws Exception {
        byte[] file;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet().createRow(0).createCell(0).setCellValue("ho_ten");
            workbook.write(output); file = output.toByteArray();
        }
        AdmissionParseResult result = parser.parse(new ByteArrayInputStream(file));
        assertThat(result.candidates()).isEmpty();
        assertThat(result.errors()).singleElement().satisfies(error -> assertThat(error.field()).isEqualTo("file"));
    }

    private void row(org.apache.poi.ss.usermodel.Row row, String... values) {
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
    }
}
