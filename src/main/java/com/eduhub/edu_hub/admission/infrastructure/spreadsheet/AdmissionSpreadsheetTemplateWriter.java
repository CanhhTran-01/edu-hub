package com.eduhub.edu_hub.admission.infrastructure.spreadsheet;

import static com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetReader.HEADERS;
import static com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetReader.HEADER_ROW_INDEX;

import com.eduhub.edu_hub.admission.application.exception.AdmissionSpreadsheetException;
import com.eduhub.edu_hub.shared.exception.*;
import java.io.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class AdmissionSpreadsheetTemplateWriter {

    public byte[] writeTemplate(AdmissionSpreadsheetScope spreadsheetScope) {
        try (var workbook = new XSSFWorkbook();
                var outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Sinh viên trúng tuyển");

            sheet.createRow(0).createCell(0).setCellValue("Sinh viên trúng tuyển");
            writeMetadataRow(sheet, 1, "Tên ngành", spreadsheetScope.majorName());
            writeMetadataRow(sheet, 2, "Mã ngành", spreadsheetScope.majorCode());
            sheet.createRow(3);

            Row headerRow = sheet.createRow(HEADER_ROW_INDEX);
            for (int columnIndex = 0; columnIndex < HEADERS.size(); columnIndex++) {
                headerRow
                        .createCell(columnIndex)
                        .setCellValue(HEADERS.get(columnIndex)); // đặt value cho từng ô trong header
                sheet.setColumnWidth(columnIndex, 24 * 256); // Đặt độ rộng cho cột thứ i
            }

            CellStyle identifierTextStyle = workbook.createCellStyle();

            // Đặt định dạng mặc định kiểu Text cho toàn bộ dữ liệu ở các cột thứ 0, 1, và 7
            identifierTextStyle.setDataFormat(
                    workbook.createDataFormat().getFormat("@")); // @ đại diện cho định dạng Text
            sheet.setDefaultColumnStyle(0, identifierTextStyle);
            sheet.setDefaultColumnStyle(1, identifierTextStyle);
            sheet.setDefaultColumnStyle(7, identifierTextStyle);
            sheet.createFreezePane(0, HEADER_ROW_INDEX + 1);

            // Ghi dữ liệu và Trả về mảng Byte
            workbook.write(outputStream);
            return outputStream.toByteArray();

        } catch (IOException exception) {
            throw new AdmissionSpreadsheetException(ErrorCode.UNEXPECTED_ERROR, exception);
        }
    }

    private void writeMetadataRow(Sheet sheet, int rowIndex, String metadataLabel, String metadataValue) {
        Row metadataRow = sheet.createRow(rowIndex);
        metadataRow.createCell(0).setCellValue(metadataLabel);
        metadataRow.createCell(1).setCellValue(metadataValue);
    }
}
