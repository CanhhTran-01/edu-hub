package com.eduhub.edu_hub.admission.infrastructure.spreadsheet;

import static com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetColumn.*;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;

@RequiredArgsConstructor
final class AdmissionSpreadsheetRowParser {
    private static final DateTimeFormatter DAY_MONTH_YEAR_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private final Map<AdmissionSpreadsheetColumn, Integer> columnIndexes;
    private final DataFormatter cellFormatter;

    AdmissionSpreadsheetApplicant parseApplicantRow(Row row) {
        AdmissionSpreadsheetApplicant applicant =
                AdmissionSpreadsheetApplicant.builder().build();

        // Lưu đúng số dòng vật lý trong Excel để Admin tìm được hồ sơ lỗi.
        applicant.setRowNumber(row.getRowNum() + 1);
        try {
            populatePersonalInformation(row, applicant);
            applicant.setEntranceScore(readEntranceScore(row));
            applicant.setDateOfBirth(readDateOfBirth(row));

            // Kiểm tra các ô công thức hoặc ô lỗi của hàng vừa xử lý
            validateLiteralCells(row);
        } catch (RuntimeException exception) {
            applicant.setState(AdmissionApplicantStatus.INVALID);
            applicant.setErrorMessage(resolveRowErrorMessage(exception));
        }
        return applicant;
    }

    boolean isBlankRow(Row row) {
        for (Cell cell : row) if (!cellFormatter.formatCellValue(cell).isBlank()) return false;
        return true;
    }

    private void populatePersonalInformation(Row row, AdmissionSpreadsheetApplicant applicant) {
        applicant.setApplicationCode(TextsNormalizer.normalizeKey(readCellText(row, APPLICATION_CODE)));
        applicant.setCitizenId(readCitizenId(row));
        applicant.setFullName(TextsNormalizer.normalizeOptionalText(readCellText(row, FULL_NAME)));
        applicant.setEmail(TextsNormalizer.normalizeEmail(readCellText(row, EMAIL)));
        applicant.setPhone(TextsNormalizer.normalizeOptionalText(readCellText(row, PHONE_NUMBER)));
        applicant.setGender(TextsNormalizer.normalizeOptionalText(readCellText(row, GENDER)));
    }

    private String readCitizenId(Row row) {
        Cell citizenIdCell = findCell(row, CITIZEN_ID);
        if (citizenIdCell != null
                && citizenIdCell.getCellType() != CellType.STRING
                && citizenIdCell.getCellType() != CellType.BLANK) {
            throw new IllegalArgumentException("CCCD phải là ô văn bản để giữ số 0 đầu");
        }
        return TextsNormalizer.normalizeCitizenId(readCellText(row, CITIZEN_ID));
    }

    private BigDecimal readEntranceScore(Row row) {
        String scoreText = readCellText(row, ENTRANCE_SCORE);
        return scoreText == null ? null : new BigDecimal(scoreText.replace(',', '.'));
    }

    private LocalDate readDateOfBirth(Row row) {
        /*
         * Xử lý ngày sinh:
         * - Trường hợp 1: Excel lưu dạng số chuẩn Date --> chuyển trực tiếp.
         * - Trường hợp 2: Người dùng gõ text thủ công --> dùng hàm parseDate để ép kiểu.
         */
        Cell dateOfBirthCell = findCell(row, DATE_OF_BIRTH);
        if (dateOfBirthCell != null
                && dateOfBirthCell.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(dateOfBirthCell)) {
            return dateOfBirthCell.getLocalDateTimeCellValue().toLocalDate();
        }
        String dateText = readCellText(row, DATE_OF_BIRTH);
        return dateText == null ? null : parseDate(dateText);
    }

    private LocalDate parseDate(String dateText) {
        try {
            return LocalDate.parse(dateText);
        } catch (DateTimeParseException exception) {
            return LocalDate.parse(dateText, DAY_MONTH_YEAR_FORMAT);
        }
    }

    private void validateLiteralCells(Row row) {
        for (Cell cell : row) {
            if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR) {
                throw new IllegalArgumentException("Không chấp nhận ô công thức hoặc ô lỗi");
            }
        }
    }

    private String resolveRowErrorMessage(RuntimeException exception) {
        return exception instanceof IllegalArgumentException
                        && exception.getMessage() != null
                        && exception.getMessage().startsWith("CCCD")
                ? exception.getMessage()
                : "Dữ liệu ô, điểm hoặc ngày sinh không hợp lệ; không dùng công thức";
    }

    private Cell findCell(Row row, AdmissionSpreadsheetColumn column) {
        Integer columnIndex = columnIndexes.get(column);
        return columnIndex == null ? null : row.getCell(columnIndex);
    }

    private String readCellText(Row row, AdmissionSpreadsheetColumn column) {
        Cell cell = findCell(row, column);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR) {
            throw new IllegalArgumentException("Không chấp nhận công thức");
        }
        return TextsNormalizer.normalizeOptionalText(cellFormatter.formatCellValue(cell));
    }
}
