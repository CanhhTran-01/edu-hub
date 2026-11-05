package com.eduhub.edu_hub.people.application.admission;

import com.eduhub.edu_hub.people.api.dto.response.AdmissionErrorResponse;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class StudentAdmissionSpreadsheetParser {
    public static final List<String> HEADERS = List.of(
            "ma_ho_so",
            "ho_ten",
            "ngay_sinh",
            "gioi_tinh",
            "email",
            "diem_xet_tuyen",
            "ma_nganh",
            "ma_khoa",
            "he_dao_tao");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

    public AdmissionParseResult parse(InputStream input) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0) return structureError("File không có sheet dữ liệu");
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) return structureError("Thiếu dòng tiêu đề");
            Map<String, Integer> columns = new LinkedHashMap<>();
            for (Cell cell : header) columns.put(text(cell).toLowerCase(Locale.ROOT), cell.getColumnIndex());
            List<String> missing =
                    HEADERS.stream().filter(name -> !columns.containsKey(name)).toList();
            if (!missing.isEmpty()) return structureError("Thiếu cột: " + String.join(", ", missing));

            List<AdmissionCandidate> candidates = new ArrayList<>();
            List<AdmissionErrorResponse> errors = new ArrayList<>();
            int total = 0;
            for (int index = header.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || isBlank(row, columns)) continue;
                total++;
                AdmissionCandidate candidate = parseRow(row, columns, errors);
                if (errors.stream().noneMatch(error -> error.rowNumber() == row.getRowNum() + 1)) {
                    candidates.add(candidate);
                }
            }
            return new AdmissionParseResult(total, candidates, errors);
        }
    }

    private AdmissionCandidate parseRow(Row row, Map<String, Integer> columns, List<AdmissionErrorResponse> errors) {
        AdmissionCandidate value = new AdmissionCandidate();
        value.setRowNumber(row.getRowNum() + 1);
        value.setApplicationCode(required(row, columns, "ma_ho_so", errors, null));
        value.setFullName(required(row, columns, "ho_ten", errors, value.getApplicationCode()));
        value.setGender(required(row, columns, "gioi_tinh", errors, value.getApplicationCode()));
        value.setEmail(required(row, columns, "email", errors, value.getApplicationCode()));
        value.setMajorCode(required(row, columns, "ma_nganh", errors, value.getApplicationCode()));
        value.setCohortCode(required(row, columns, "ma_khoa", errors, value.getApplicationCode()));
        value.setTrainingType(required(row, columns, "he_dao_tao", errors, value.getApplicationCode()));
        Cell dateCell = row.getCell(columns.get("ngay_sinh"));
        try {
            if (dateCell != null
                    && dateCell.getCellType() == CellType.NUMERIC
                    && DateUtil.isCellDateFormatted(dateCell)) {
                value.setDateOfBirth(dateCell.getLocalDateTimeCellValue().toLocalDate());
            } else {
                value.setDateOfBirth(LocalDate.parse(text(dateCell), DATE_FORMAT));
            }
        } catch (DateTimeParseException | IllegalStateException exception) {
            errors.add(error(row, value.getApplicationCode(), "ngay_sinh", "Ngày sinh phải có định dạng dd/MM/yyyy"));
        }
        try {
            String score = required(row, columns, "diem_xet_tuyen", errors, value.getApplicationCode());
            if (score != null) {
                value.setEntranceScore(new BigDecimal(score.replace(',', '.')));
                if (value.getEntranceScore().signum() < 0) throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            errors.add(error(row, value.getApplicationCode(), "diem_xet_tuyen", "Điểm xét tuyển phải là số không âm"));
        }
        return value;
    }

    private String required(
            Row row, Map<String, Integer> columns, String field, List<AdmissionErrorResponse> errors, String code) {
        String value = text(row.getCell(columns.get(field))).trim();
        if (value.isBlank()) {
            errors.add(error(row, code, field, "Thiếu dữ liệu bắt buộc"));
            return null;
        }
        return value;
    }

    private boolean isBlank(Row row, Map<String, Integer> columns) {
        return columns.values().stream()
                .allMatch(index -> text(row.getCell(index)).isBlank());
    }

    private String text(Cell cell) {
        return cell == null ? "" : formatter.formatCellValue(cell);
    }

    private AdmissionErrorResponse error(Row row, String code, String field, String reason) {
        return new AdmissionErrorResponse(row.getRowNum() + 1, code, field, reason);
    }

    private AdmissionParseResult structureError(String reason) {
        return new AdmissionParseResult(0, List.of(), List.of(new AdmissionErrorResponse(1, null, "file", reason)));
    }
}
