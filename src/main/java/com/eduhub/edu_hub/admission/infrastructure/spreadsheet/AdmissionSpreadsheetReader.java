package com.eduhub.edu_hub.admission.infrastructure.spreadsheet;

import com.eduhub.edu_hub.admission.application.command.AdmissionImportRow;
import com.eduhub.edu_hub.admission.application.command.AdmissionImportScope;
import com.eduhub.edu_hub.admission.infrastructure.config.AdmissionSettings;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import java.io.IOException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class AdmissionSpreadsheetReader {
    private final AdmissionSettings admissionSettings;
    public static final int HEADER_ROW_INDEX = 4;

    public static final List<String> HEADERS = Arrays.stream(AdmissionSpreadsheetColumn.values())
            .map(AdmissionSpreadsheetColumn::headerTitle)
            .toList();

    public List<AdmissionImportRow> parse(MultipartFile file, AdmissionImportScope expectedScope) {
        // validate file trước khi xử lý
        validateUploadedFile(file);

        // Mở file xlsx upload bằng try-with-resources
        // input và workbook tự động được đóng khi kết thúc khối try, tránh rò rỉ bộ nhớ
        try (var input = file.getInputStream();
                var workbook = new XSSFWorkbook(input)) {
            Sheet sheet = requireSingleSheetWithinRowLimit(workbook);

            // Apache POI tool giúp đọc value ô (Cell) dưới dạng String đúng như cách hiển thị trên Excel
            DataFormatter cellFormatter = new DataFormatter(Locale.ROOT);

            // Sai ngành/khóa là lỗi toàn file, phải chặn trước khi đọc từng hồ sơ.
            new AdmissionSpreadsheetScopeValidator().validateSelectedScope(sheet, cellFormatter, expectedScope);

            // Đọc hàng header, dựng bản đồ cột (map từng ô của hàng header với chỉ số cụ thể)
            Map<AdmissionSpreadsheetColumn, Integer> columnIndexes = readHeaderColumnIndexes(sheet, cellFormatter);
            return readApplicantRows(sheet, new AdmissionSpreadsheetRowParser(columnIndexes, cellFormatter));
        } catch (BaseException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new BaseException(ErrorCode.ADMISSION_IMPORT_INVALID);
        }
    }

    private void validateUploadedFile(MultipartFile file) {
        if (file == null
                || file.isEmpty()
                || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BaseException(ErrorCode.ADMISSION_IMPORT_INVALID);
        }
    }

    private Sheet requireSingleSheetWithinRowLimit(Workbook workbook) {
        // phải đúng 1 sheet
        if (workbook.getNumberOfSheets() != 1) {
            throw new BaseException(ErrorCode.ADMISSION_IMPORT_INVALID);
        }

        // Không tính khối thông tin chung và hàng header vào giới hạn số dòng hồ sơ.
        Sheet sheet = workbook.getSheetAt(0);
        if (sheet.getLastRowNum() - HEADER_ROW_INDEX > admissionSettings.getMaxImportRows()) {
            throw new BaseException(ErrorCode.ADMISSION_IMPORT_INVALID);
        }
        return sheet;
    }

    private Map<AdmissionSpreadsheetColumn, Integer> readHeaderColumnIndexes(Sheet sheet, DataFormatter cellFormatter) {
        Map<AdmissionSpreadsheetColumn, Integer> columnIndexes = new EnumMap<>(AdmissionSpreadsheetColumn.class);
        Map<String, Integer> indexesByHeader = new HashMap<>(); // key=tên cột, value=chỉ số vị trí của cột (từ 0)
        Row headerRow = sheet.getRow(HEADER_ROW_INDEX);
        if (headerRow == null) {
            throw new BaseException(ErrorCode.ADMISSION_IMPORT_INVALID);
        }

        // duyệt từng ô của hàng header để dựng chỉ số
        for (Cell headerCell : headerRow) {
            if (headerCell.getCellType() == CellType.FORMULA || headerCell.getCellType() == CellType.ERROR) {
                throw new BaseException(ErrorCode.ADMISSION_IMPORT_INVALID);
            }
            String headerTitle = TextsNormalizer.normalizeKey(cellFormatter.formatCellValue(headerCell));
            AdmissionSpreadsheetColumn column = AdmissionSpreadsheetColumn.fromHeaderTitle(headerTitle);
            String canonicalTitle = column == null ? headerTitle : TextsNormalizer.normalizeKey(column.headerTitle());
            if (indexesByHeader.put(canonicalTitle, headerCell.getColumnIndex()) != null) {
                // phát hiện cột trùng tên, nhả lỗi
                throw new BaseException(ErrorCode.ADMISSION_IMPORT_INVALID);
            }
            if (column != null) columnIndexes.put(column, headerCell.getColumnIndex());
        }

        // 4 cột đầu của HEADERS phải có, còn lại tùy chọn
        if (!columnIndexes
                .keySet()
                .containsAll(Arrays.asList(AdmissionSpreadsheetColumn.values()).subList(0, 4))) {
            throw new BaseException(ErrorCode.ADMISSION_IMPORT_INVALID);
        }
        return columnIndexes;
    }

    private List<AdmissionImportRow> readApplicantRows(Sheet sheet, AdmissionSpreadsheetRowParser rowParser) {
        List<AdmissionImportRow> importRows = new ArrayList<>();

        // đọc từng dòng dữ liệu
        for (int rowIndex = HEADER_ROW_INDEX + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);

            // bỏ qua dòng trống
            if (row == null || rowParser.isBlankRow(row)) continue;
            importRows.add(rowParser.parseApplicantRow(row));
        }
        return importRows;
    }
}
