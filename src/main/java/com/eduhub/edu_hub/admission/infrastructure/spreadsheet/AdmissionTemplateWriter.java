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
public class AdmissionTemplateWriter {
    public byte[] write(AdmissionImportScope scope) {
        try (var workbook = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Sinh vien trung tuyen");

            metadata(sheet, 0, "ma_nganh", scope.majorCode());
            metadata(sheet, 1, "ten_nganh", scope.majorName());
            metadata(sheet, 2, "ma_khoa", scope.cohortCode());
            sheet.createRow(3)
                    .createCell(0)
                    .setCellValue("Giữ nguyên ma_nganh/ma_khoa; nhập danh sách sinh viên từ dòng 6.");

            Row row = sheet.createRow(HEADER_ROW_INDEX);

            for (int i = 0; i < HEADERS.size(); i++) {
                row.createCell(i).setCellValue(HEADERS.get(i)); // đặt value cho từng ô trong header
                sheet.setColumnWidth(i, 24 * 256); // Đặt độ rộng cho cột thứ i
            }

            CellStyle text = workbook.createCellStyle();

            // Đặt định dạng mặc định kiểu Text cho toàn bộ dữ liệu ở các cột thứ 0, 1, và 7
            text.setDataFormat(workbook.createDataFormat().getFormat("@")); // @ đại diện cho định dạng Text
            sheet.setDefaultColumnStyle(0, text);
            sheet.setDefaultColumnStyle(1, text);
            sheet.setDefaultColumnStyle(7, text);
            sheet.createFreezePane(0, HEADER_ROW_INDEX + 1);

            // Ghi dữ liệu và Trả về mảng Byte
            workbook.write(output);
            return output.toByteArray();

        } catch (IOException exception) {
            throw new BaseException(ErrorCode.UNEXPECTED_ERROR);
        }
    }

    private void metadata(Sheet sheet, int index, String label, String value) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }
}
