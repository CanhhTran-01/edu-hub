package com.eduhub.edu_hub.admission.infrastructure.spreadsheet;

import static com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetReader.HEADERS;
import static com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetReader.HEADER_ROW_INDEX;

import com.eduhub.edu_hub.admission.application.command.AdmissionImportScope;
import com.eduhub.edu_hub.shared.exception.*;
import java.io.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class AdmissionSpreadsheetTemplateWriter {
    public byte[] writeTemplate(AdmissionImportScope importScope) {
        try (var workbook = new XSSFWorkbook();
                var outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Sinh vien trung tuyen");

            writeMetadataRow(sheet, 0, "ma_nganh", importScope.majorCode());
            writeMetadataRow(sheet, 1, "ten_nganh", importScope.majorName());
            writeMetadataRow(sheet, 2, "ma_khoa", importScope.cohortCode());
            sheet.createRow(3)
                    .createCell(0)
                    .setCellValue("Giữ nguyên ma_nganh/ma_khoa; nhập danh sách sinh viên từ dòng 6.");

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
            throw new BaseException(ErrorCode.UNEXPECTED_ERROR);
        }
    }

    private void writeMetadataRow(Sheet sheet, int rowIndex, String metadataLabel, String metadataValue) {
        Row metadataRow = sheet.createRow(rowIndex);
        metadataRow.createCell(0).setCellValue(metadataLabel);
        metadataRow.createCell(1).setCellValue(metadataValue);
    }
}
