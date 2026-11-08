package com.eduhub.edu_hub.admission.infrastructure.spreadsheet;

import static com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetReader.HEADER_ROW_INDEX;

import com.eduhub.edu_hub.admission.application.exception.AdmissionImportScopeException;
import com.eduhub.edu_hub.admission.application.exception.AdmissionSpreadsheetException;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.*;

final class AdmissionSpreadsheetScopeValidator {
    // Mỗi file dành cho đúng một ngành và một khóa đã chọn từ danh mục.
    void validateSelectedScope(Sheet sheet, DataFormatter cellFormatter, AdmissionSpreadsheetScope expectedScope) {
        // Kiểm tra các ô trong phần metadata
        validateMetadataCells(sheet);

        Row firstRow = sheet.getRow(0);
        boolean isLegacyTemplate = firstRow != null
                && TextsNormalizer.normalizeKey("ma_nganh")
                        .equals(TextsNormalizer.normalizeKey(cellFormatter.formatCellValue(firstRow.getCell(0))));
        String fileMajorCode;
        String fileCohortCode = null;
        if (isLegacyTemplate) {
            // Đọc mã ngành và mã khóa từ Excel
            fileMajorCode = readRequiredMetadataValue(sheet, 0, "ma_nganh", cellFormatter);
            fileCohortCode = readRequiredMetadataValue(sheet, 2, "ma_khoa", cellFormatter);
        } else {
            // Mẫu mới chỉ có mã ngành; khóa lấy từ lựa chọn/plan trên hệ thống, không suy từ file.
            // Mẫu cũ vẫn phải đủ mã khóa và được đối chiếu như trước.
            fileMajorCode = readRequiredMetadataValue(sheet, 2, "Mã ngành", cellFormatter);
        }

        // check xem có khớp với lựa chọn từ Admin
        List<FieldErrorDetail> scopeErrors = new ArrayList<>();
        if (!TextsNormalizer.normalizeKey(expectedScope.majorCode())
                .equals(TextsNormalizer.normalizeKey(fileMajorCode))) {
            scopeErrors.add(new FieldErrorDetail(
                    "majorCode",
                    "Ngành trong file: " + fileMajorCode + "; ngành đã chọn: " + expectedScope.majorCode()));
        }
        if (fileCohortCode != null
                && !TextsNormalizer.normalizeKey(expectedScope.cohortCode())
                        .equals(TextsNormalizer.normalizeKey(fileCohortCode))) {
            scopeErrors.add(new FieldErrorDetail(
                    "cohortCode",
                    "Khóa trong file: " + fileCohortCode + "; khóa đã chọn: " + expectedScope.cohortCode()));
        }
        if (!scopeErrors.isEmpty()) throw new AdmissionImportScopeException(scopeErrors);

        /* Ngành và khóa phải được xác định bởi dữ liệu đã chọn, không phải do file Excel quyết định.
        Ví dụ, Admin đang import vào kế hoạch ngành CNTT. Nếu file khai báo ngành QTKD,
        hệ thống phải từ chối, chứ không tự động chuyển sang QTKD. **/
    }

    private void validateMetadataCells(Sheet sheet) {
        for (int rowIndex = 0; rowIndex < HEADER_ROW_INDEX; rowIndex++) {
            Row metadataRow = sheet.getRow(rowIndex);
            if (metadataRow == null) continue;
            for (Cell metadataCell : metadataRow) {
                if (metadataCell.getCellType() == CellType.FORMULA || metadataCell.getCellType() == CellType.ERROR) {
                    throw new AdmissionSpreadsheetException(ErrorCode.ADMISSION_IMPORT_INVALID);
                }
            }
        }
    }

    private String readRequiredMetadataValue(
            Sheet sheet, int rowIndex, String expectedLabel, DataFormatter cellFormatter) {
        Row metadataRow = sheet.getRow(rowIndex);
        if (metadataRow == null
                || !TextsNormalizer.normalizeKey(expectedLabel)
                        .equals(TextsNormalizer.normalizeKey(cellFormatter.formatCellValue(metadataRow.getCell(0))))) {
            throw new AdmissionSpreadsheetException(ErrorCode.ADMISSION_IMPORT_METADATA_REQUIRED);
        }
        String metadataValue =
                TextsNormalizer.normalizeOptionalText(cellFormatter.formatCellValue(metadataRow.getCell(1)));
        if (metadataValue == null)
            throw new AdmissionSpreadsheetException(ErrorCode.ADMISSION_IMPORT_METADATA_REQUIRED);
        return metadataValue;
    }
}
